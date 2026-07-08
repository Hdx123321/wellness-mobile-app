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
  private static final int SEMANTIC_CANDIDATE_LIMIT = 80;

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
    RetrievalIntent intent = classifyIntent(question);

    context.append("=== Retrieved Wellness Context ===\n")
        .append("Use raw recent data as the most reliable source. Historical summaries are memory aids, ")
        .append("not current facts. Mention uncertainty when sources are old, summarized, or incomplete.\n")
        .append("retrieval_intent=").append(intent.name()).append("\n\n");

    // 1. Semantic retrieval
    if (intent.includeSemanticSearch()) {
      try {
      float[] questionEmbedding = embeddingClient.embed(question);
      List<RagDocument> docs = ragDocuments.findTop80ByUserIdOrderByCreatedAtDesc(userId).stream()
          .filter(doc -> intent.allows(doc))
          .limit(SEMANTIC_CANDIDATE_LIMIT)
          .toList();
      if (!docs.isEmpty()) {
        List<RagDocument> topDocs = rankBySimilarity(docs, questionEmbedding, SEMANTIC_TOP_K);
        if (!topDocs.isEmpty()) {
          context.append("=== Historical Summaries (semantic recall) ===\n");
          for (RagDocument doc : topDocs) {
            appendDocumentContext(context, doc);
          }
        }
      }
      } catch (Exception e) {
        log.warn("Semantic retrieval failed for user {}, falling back to structured only: {}",
            userId, e.getMessage());
      }
    }

    // 2. Structured retrieval (30-day aggregation with anomalies)
    try {
      String structured = buildStructuredContext(userId);
      if (!structured.isBlank()) {
        context.append("=== Raw Recent Tracker Data ===\n");
        context.append("source=tracker_entries, window_days=").append(STRUCTURED_DAYS)
            .append(", generated_at=").append(Instant.now()).append("\n");
        context.append(structured);
      }
    } catch (Exception e) {
      log.warn("Structured retrieval failed for user {}: {}", userId, e.getMessage());
    }

    return context.toString();
  }

  private void appendDocumentContext(StringBuilder context, RagDocument doc) {
    Map<String, String> metadata = metadata(doc);
    context.append("source_doc_id=").append(doc.getId())
        .append(", doc_type=").append(doc.getDocType())
        .append(", source_key=").append(doc.getSourceKey())
        .append(", title=\"").append(doc.getTitle()).append("\"")
        .append(", created_at=").append(doc.getCreatedAt());
    appendMetadata(context, metadata, "coveredFrom");
    appendMetadata(context, metadata, "coveredTo");
    appendMetadata(context, metadata, "weekStart");
    appendMetadata(context, metadata, "weekEnd");
    appendMetadata(context, metadata, "month");
    appendMetadata(context, metadata, "year");
    appendMetadata(context, metadata, "sessionId");
    appendMetadata(context, metadata, "sourceScope");
    appendMetadata(context, metadata, "summarySchemaVersion");
    appendMetadata(context, metadata, "structuredStats");
    context.append("\nsummary=").append(doc.getContent()).append("\n\n");
  }

  private void appendMetadata(StringBuilder context, Map<String, String> metadata, String key) {
    String value = metadata.get(key);
    if (value != null && !value.isBlank()) {
      context.append(", ").append(key).append("=").append(value);
    }
  }

  private Map<String, String> metadata(RagDocument doc) {
    if (doc.getMetadata() == null || doc.getMetadata().isBlank()) return Map.of();
    try {
      Map<String, Object> raw = mapper.readValue(doc.getMetadata(),
          new TypeReference<Map<String, Object>>() {});
      Map<String, String> result = new LinkedHashMap<>();
      raw.forEach((key, value) -> {
        if (value != null) result.put(key, value.toString());
      });
      return result;
    } catch (Exception e) {
      return Map.of();
    }
  }

  private RetrievalIntent classifyIntent(String question) {
    String q = question == null ? "" : question.toLowerCase();
    if (containsAny(q, "record", "log", "save", "track", "delete", "remove", "update", "change", "fix")) {
      return RetrievalIntent.ACTION;
    }
    if (containsAny(q, "previously", "before", "earlier", "last time", "we discussed", "we talked",
        "conversation", "remember", "之前", "上次", "聊过", "记得")) {
      return RetrievalIntent.CONVERSATION_MEMORY;
    }
    if (containsAny(q, "month", "months", "long term", "long-term", "trend over", "overall",
        "history", "historical", "长期", "几个月", "历史", "总体")) {
      return RetrievalIntent.LONG_TERM;
    }
    if (containsAny(q, "today", "recent", "recently", "this week", "last 7", "last week",
        "最近", "今天", "本周", "这周", "上周")) {
      return RetrievalIntent.RECENT;
    }
    return RetrievalIntent.GENERAL;
  }

  private boolean containsAny(String text, String... needles) {
    for (String needle : needles) {
      if (text.contains(needle)) return true;
    }
    return false;
  }

  private enum RetrievalIntent {
    ACTION {
      @Override boolean includeSemanticSearch() { return false; }
      @Override boolean allows(RagDocument doc) { return false; }
    },
    RECENT {
      @Override boolean allows(RagDocument doc) {
        return "WEEKLY_REPORT".equals(doc.getDocType());
      }
    },
    LONG_TERM {
      @Override boolean allows(RagDocument doc) {
        return "WEEKLY_REPORT".equals(doc.getDocType()) || "MONTHLY_REPORT".equals(doc.getDocType());
      }
    },
    CONVERSATION_MEMORY {
      @Override boolean allows(RagDocument doc) {
        return "CONVERSATION_SUMMARY".equals(doc.getDocType());
      }
    },
    GENERAL {
      @Override boolean allows(RagDocument doc) { return true; }
    };

    boolean includeSemanticSearch() { return true; }
    abstract boolean allows(RagDocument doc);
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
