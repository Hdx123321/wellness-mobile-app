package com.wellnessmate.advisor.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wellnessmate.advisor.domain.RagDocument;
import com.wellnessmate.advisor.repository.RagDocumentRepository;
import com.wellnessmate.food.domain.FoodEntry;
import com.wellnessmate.food.domain.FoodEntryItem;
import com.wellnessmate.food.repository.FoodEntryItemRepository;
import com.wellnessmate.food.repository.FoodEntryRepository;
import com.wellnessmate.tracker.domain.TrackerEntry;
import com.wellnessmate.tracker.domain.TrackerType;
import com.wellnessmate.tracker.repository.TrackerEntryRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

/**
 * Hybrid RAG retrieval: semantic (embedding + cosine similarity) + structured (SQL aggregation).
 * Returns a formatted context string to inject into the LLM prompt.
 */
@Service
public class RagRetrievalService {

  private static final Logger log = LoggerFactory.getLogger(RagRetrievalService.class);
  private static final int SEMANTIC_TOP_K = 5;
  private static final int STRUCTURED_DAYS = 30;

  private final RagDocumentRepository ragDocuments;
  private final TrackerEntryRepository trackerEntries;
  private final FoodEntryRepository foodEntries;
  private final FoodEntryItemRepository foodItems;
  private final EmbeddingClient embeddingClient;
  private final ObjectMapper mapper;

  public RagRetrievalService(RagDocumentRepository ragDocuments,
                             TrackerEntryRepository trackerEntries,
                             FoodEntryRepository foodEntries,
                             FoodEntryItemRepository foodItems,
                             EmbeddingClient embeddingClient,
                             ObjectMapper mapper) {
    this.ragDocuments = ragDocuments;
    this.trackerEntries = trackerEntries;
    this.foodEntries = foodEntries;
    this.foodItems = foodItems;
    this.embeddingClient = embeddingClient;
    this.mapper = mapper;
  }

  /**
   * Build a RAG context string for the given user question.
   * Silently degrades if embedding or retrieval fails.
   */
  public String retrieve(Long userId, String question) {
    StringBuilder context = new StringBuilder();

    // 1. Semantic retrieval
    try {
      float[] questionEmbedding = embeddingClient.embed(question);
      List<RagDocument> docs = ragDocuments.findByUserIdOrderByCreatedAtDesc(userId);
      if (!docs.isEmpty()) {
        List<RagDocument> topDocs = rankBySimilarity(docs, questionEmbedding, SEMANTIC_TOP_K);
        if (!topDocs.isEmpty()) {
          context.append("=== Relevant Historical Summaries ===\n");
          for (RagDocument doc : topDocs) {
            context.append("[").append(doc.getDocType()).append("] ")
                .append(doc.getTitle()).append(": ")
                .append(doc.getContent()).append("\n\n");
          }
        }
      }
    } catch (Exception e) {
      log.warn("Semantic retrieval failed for user {}, falling back to structured only: {}",
          userId, e.getMessage());
    }

    // 2. Structured retrieval (30-day aggregation with anomalies)
    try {
      String structured = buildStructuredContext(userId);
      if (!structured.isBlank()) {
        context.append("=== Recent Data (30 days) ===\n");
        context.append(structured);
      }
    } catch (Exception e) {
      log.warn("Structured retrieval failed for user {}: {}", userId, e.getMessage());
    }

    return context.toString();
  }

  /**
   * Query a single tracker type for the given number of days.
   * Returns aggregated stats, recent individual entries (with IDs for update/delete),
   * and anomaly markers. Used by the Agent tool system.
   */
  public String queryTrackerType(Long userId, TrackerType type, int days) {
    Instant now = Instant.now();
    Instant from = now.minus(days, ChronoUnit.DAYS);

    var page = trackerEntries.findOwned(userId, type, from, now,
        PageRequest.of(0, 500, Sort.by(Sort.Direction.ASC, "recordedAt")));
    List<TrackerEntry> entries = page.getContent();

    if (entries.isEmpty()) {
      return "No " + type.name() + " data in the last " + days + " days.";
    }

    List<BigDecimal> amounts = entries.stream().map(TrackerEntry::getAmount).toList();
    BigDecimal avg = average(amounts);
    BigDecimal min = amounts.stream().min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
    BigDecimal max = amounts.stream().max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
    BigDecimal stddev = stddev(amounts, avg);
    String trend = trend(amounts);

    StringBuilder sb = new StringBuilder();
    sb.append(type.name()).append(" stats (last ").append(days).append(" days): ")
        .append(entries.size()).append(" entries, ")
        .append("avg=").append(avg).append(type.unit())
        .append(", min=").append(min).append(type.unit())
        .append(", max=").append(max).append(type.unit())
        .append(", stddev=").append(stddev).append(type.unit())
        .append(", trend=").append(trend).append("\n");

    // Anomalies
    for (TrackerEntry entry : entries) {
      BigDecimal threshold = avg.add(stddev.multiply(BigDecimal.valueOf(2)));
      if (entry.getAmount().compareTo(threshold) > 0) {
        sb.append("⚠ High anomaly: #").append(entry.getId())
            .append(" ").append(entry.getRecordedAt())
            .append(" ").append(entry.getAmount()).append(type.unit());
        if (entry.getNotes() != null && !entry.getNotes().isBlank())
          sb.append(" (").append(entry.getNotes()).append(")");
        sb.append("\n");
      }
      BigDecimal lowThreshold = avg.subtract(stddev.multiply(BigDecimal.valueOf(2)));
      if (entry.getAmount().compareTo(lowThreshold) < 0 && entry.getAmount().compareTo(BigDecimal.ZERO) > 0) {
        sb.append("⚠ Low anomaly: #").append(entry.getId())
            .append(" ").append(entry.getRecordedAt())
            .append(" ").append(entry.getAmount()).append(type.unit());
        if (entry.getNotes() != null && !entry.getNotes().isBlank())
          sb.append(" (").append(entry.getNotes()).append(")");
        sb.append("\n");
      }
    }

    // Recent individual entries (with IDs for potential update/delete)
    List<TrackerEntry> recent = entries.stream()
        .sorted(Comparator.comparing(TrackerEntry::getRecordedAt).reversed())
        .limit(20).toList();
    sb.append("\nRecent entries:\n");
    for (TrackerEntry e : recent) {
      sb.append("  #").append(e.getId()).append(": ")
          .append(e.getRecordedAt()).append(" ")
          .append(e.getAmount()).append(type.unit());
      if (e.getNotes() != null && !e.getNotes().isBlank())
        sb.append(" (").append(e.getNotes()).append(")");
      sb.append("\n");
    }

    // FOOD: also query FoodEntry + FoodEntryItem for detailed nutrition
    if (type == TrackerType.FOOD) {
      List<FoodEntry> foods = foodEntries
          .findByUserIdAndRecordedAtGreaterThanEqualAndRecordedAtLessThanOrderByRecordedAtDesc(
              userId, from, now);
      if (!foods.isEmpty()) {
        List<Long> entryIds = foods.stream().map(FoodEntry::getId).toList();
        List<FoodEntryItem> items = foodItems.findByFoodEntryIdInOrderById(entryIds);
        sb.append("\nDetailed food entries:\n");
        for (FoodEntry food : foods) {
          List<FoodEntryItem> mealItems = items.stream()
              .filter(i -> i.getFoodEntryId().equals(food.getId())).toList();
          BigDecimal cal = mealItems.stream().map(FoodEntryItem::getCalories)
              .reduce(BigDecimal.ZERO, BigDecimal::add);
          BigDecimal pro = mealItems.stream().map(FoodEntryItem::getProteinGrams)
              .reduce(BigDecimal.ZERO, BigDecimal::add);
          BigDecimal carb = mealItems.stream().map(FoodEntryItem::getCarbohydrateGrams)
              .reduce(BigDecimal.ZERO, BigDecimal::add);
          BigDecimal fat = mealItems.stream().map(FoodEntryItem::getFatGrams)
              .reduce(BigDecimal.ZERO, BigDecimal::add);
          sb.append("  FoodEntry #").append(food.getId())
              .append(" (use entry_id=").append(food.getTrackerEntryId())
              .append(" for delete_tracker_entry)")
              .append(" ").append(food.getRecordedAt())
              .append(" ").append(food.getMealType())
              .append(": ").append(cal).append("kcal");
          if (pro.compareTo(BigDecimal.ZERO) > 0)
            sb.append(", P:").append(pro).append("g");
          if (carb.compareTo(BigDecimal.ZERO) > 0)
            sb.append(", C:").append(carb).append("g");
          if (fat.compareTo(BigDecimal.ZERO) > 0)
            sb.append(", F:").append(fat).append("g");
          sb.append(" [");
          sb.append(mealItems.stream()
              .map(i -> i.getFoodName() + " " + i.getCalories() + "kcal")
              .collect(Collectors.joining(", ")));
          sb.append("]");
          if (food.getNotes() != null && !food.getNotes().isBlank())
            sb.append(" (").append(food.getNotes()).append(")");
          sb.append("\n");
        }
      }
    }

    return sb.toString();
  }

  // ── Semantic search ──

  private List<RagDocument> rankBySimilarity(List<RagDocument> docs, float[] query, int topK) {
    List<DocScore> scored = new ArrayList<>();
    for (RagDocument doc : docs) {
      try {
        float[] docEmb = deserializeEmbedding(doc.getEmbedding());
        if (docEmb != null && docEmb.length == query.length) {
          double sim = cosineSimilarity(query, docEmb);
          scored.add(new DocScore(doc, sim));
        }
      } catch (Exception e) {
        // Skip documents with malformed embeddings
      }
    }
    scored.sort(Comparator.comparingDouble(DocScore::similarity).reversed());
    return scored.stream().limit(topK).map(DocScore::document).toList();
  }

  private float[] deserializeEmbedding(String json) {
    try {
      List<Double> list = mapper.readValue(json, new TypeReference<List<Double>>() {});
      float[] result = new float[list.size()];
      for (int i = 0; i < result.length; i++) {
        result[i] = list.get(i).floatValue();
      }
      return result;
    } catch (Exception e) {
      return null;
    }
  }

  private double cosineSimilarity(float[] a, float[] b) {
    double dot = 0, normA = 0, normB = 0;
    for (int i = 0; i < a.length; i++) {
      dot += (double) a[i] * b[i];
      normA += (double) a[i] * a[i];
      normB += (double) b[i] * b[i];
    }
    if (normA == 0 || normB == 0) return 0;
    return dot / (Math.sqrt(normA) * Math.sqrt(normB));
  }

  private record DocScore(RagDocument document, double similarity) {}

  // ── Structured retrieval ──

  private String buildStructuredContext(Long userId) {
    Instant now = Instant.now();
    Instant from = now.minus(STRUCTURED_DAYS, ChronoUnit.DAYS);

    var page = trackerEntries.findOwned(userId, null, from, now,
        PageRequest.of(0, 500, Sort.by(Sort.Direction.ASC, "recordedAt")));
    List<TrackerEntry> entries = page.getContent();

    if (entries.isEmpty()) return "";

    Map<TrackerType, List<TrackerEntry>> grouped = entries.stream()
        .collect(Collectors.groupingBy(TrackerEntry::getTrackerType));

    StringBuilder sb = new StringBuilder();
    for (var typeGroup : grouped.entrySet()) {
      TrackerType type = typeGroup.getKey();
      List<TrackerEntry> list = typeGroup.getValue();
      List<BigDecimal> amounts = list.stream().map(TrackerEntry::getAmount).toList();

      BigDecimal avg = average(amounts);
      BigDecimal min = amounts.stream().min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
      BigDecimal max = amounts.stream().max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
      BigDecimal stddev = stddev(amounts, avg);

      // Trend direction
      String trend = trend(amounts);

      sb.append(type.name()).append(": ")
          .append(list.size()).append(" entries, ")
          .append("avg=").append(avg).append(type.unit())
          .append(", range=").append(min).append("-").append(max).append(type.unit())
          .append(", trend=").append(trend).append("\n");

      // Anomalies: values > mean + 2*stddev
      for (TrackerEntry entry : list) {
        BigDecimal threshold = avg.add(stddev.multiply(BigDecimal.valueOf(2)));
        if (entry.getAmount().compareTo(threshold) > 0) {
          sb.append("  ⚠ High: ").append(entry.getRecordedAt())
              .append(" ").append(entry.getAmount()).append(type.unit());
          if (entry.getNotes() != null && !entry.getNotes().isBlank()) {
            sb.append(" (").append(entry.getNotes()).append(")");
          }
          sb.append("\n");
        }
        BigDecimal lowThreshold = avg.subtract(stddev.multiply(BigDecimal.valueOf(2)));
        if (entry.getAmount().compareTo(lowThreshold) < 0 && entry.getAmount().compareTo(BigDecimal.ZERO) > 0) {
          sb.append("  ⚠ Low: ").append(entry.getRecordedAt())
              .append(" ").append(entry.getAmount()).append(type.unit());
          if (entry.getNotes() != null && !entry.getNotes().isBlank()) {
            sb.append(" (").append(entry.getNotes()).append(")");
          }
          sb.append("\n");
        }
      }
    }

    // Food summary
    List<FoodEntry> foods = foodEntries
        .findByUserIdAndRecordedAtGreaterThanEqualAndRecordedAtLessThanOrderByRecordedAtDesc(
            userId, from, now);
    if (!foods.isEmpty()) {
      List<Long> entryIds = foods.stream().map(FoodEntry::getId).toList();
      List<FoodEntryItem> items = foodItems.findByFoodEntryIdInOrderById(entryIds);
      // Count distinct days that actually have food entries (not calendar days)
      long daysWithFood = foods.stream()
          .map(e -> e.getRecordedAt().atZone(ZoneOffset.UTC).toLocalDate())
          .distinct().count();
      daysWithFood = Math.max(1, daysWithFood);

      BigDecimal totalCal = BigDecimal.ZERO, totalPro = BigDecimal.ZERO;
      BigDecimal totalCarb = BigDecimal.ZERO, totalFat = BigDecimal.ZERO;
      BigDecimal totalFiber = BigDecimal.ZERO;
      for (FoodEntryItem item : items) {
        totalCal = totalCal.add(item.getCalories());
        totalPro = totalPro.add(item.getProteinGrams());
        totalCarb = totalCarb.add(item.getCarbohydrateGrams());
        totalFat = totalFat.add(item.getFatGrams());
        totalFiber = totalFiber.add(item.getFiberGrams());
      }

      sb.append("\nFOOD (daily avg over ~").append(daysWithFood).append(" tracked days): ")
          .append(totalCal.divide(BigDecimal.valueOf(daysWithFood), 0, RoundingMode.HALF_UP))
          .append(" kcal, ")
          .append(totalPro.divide(BigDecimal.valueOf(daysWithFood), 1, RoundingMode.HALF_UP))
          .append("g protein, ")
          .append(totalCarb.divide(BigDecimal.valueOf(daysWithFood), 1, RoundingMode.HALF_UP))
          .append("g carbs, ")
          .append(totalFat.divide(BigDecimal.valueOf(daysWithFood), 1, RoundingMode.HALF_UP))
          .append("g fat\n");
    }

    return sb.toString();
  }

  private BigDecimal average(List<BigDecimal> values) {
    if (values.isEmpty()) return BigDecimal.ZERO;
    BigDecimal sum = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    return sum.divide(BigDecimal.valueOf(values.size()), 2, RoundingMode.HALF_UP);
  }

  private BigDecimal stddev(List<BigDecimal> values, BigDecimal mean) {
    if (values.size() < 2) return BigDecimal.ZERO;
    BigDecimal variance = values.stream()
        .map(v -> v.subtract(mean).pow(2))
        .reduce(BigDecimal.ZERO, BigDecimal::add)
        .divide(BigDecimal.valueOf(values.size()), 4, RoundingMode.HALF_UP);
    return new BigDecimal(Math.sqrt(variance.doubleValue()))
        .setScale(2, RoundingMode.HALF_UP);
  }

  private String trend(List<BigDecimal> amounts) {
    if (amounts.size() < 4) return "—";
    int mid = amounts.size() / 2;
    BigDecimal firstHalf = average(amounts.subList(0, mid));
    BigDecimal secondHalf = average(amounts.subList(mid, amounts.size()));
    BigDecimal diff = secondHalf.subtract(firstHalf);
    if (diff.abs().compareTo(firstHalf.multiply(BigDecimal.valueOf(0.05))) < 0) return "→";
    return diff.signum() > 0 ? "↑" : "↓";
  }
}
