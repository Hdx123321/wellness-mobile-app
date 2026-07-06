package com.wellnessmate.advisor.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wellnessmate.advisor.domain.RagDocument;
import com.wellnessmate.advisor.repository.RagDocumentRepository;
import com.wellnessmate.common.api.ApiException;
import com.wellnessmate.food.domain.FoodEntry;
import com.wellnessmate.food.domain.FoodEntryItem;
import com.wellnessmate.food.repository.FoodEntryItemRepository;
import com.wellnessmate.food.repository.FoodEntryRepository;
import com.wellnessmate.tracker.domain.TrackerEntry;
import com.wellnessmate.tracker.domain.TrackerType;
import com.wellnessmate.tracker.repository.TrackerEntryRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/**
 * Generates RAG documents (weekly/monthly reports) from user tracker and food data.
 * Called lazily when the user opens the AI Advisor.
 */
@Service
public class DocumentGenerationService {

  private static final Logger log = LoggerFactory.getLogger(DocumentGenerationService.class);

  private final TrackerEntryRepository trackerEntries;
  private final FoodEntryRepository foodEntries;
  private final FoodEntryItemRepository foodItems;
  private final RagDocumentRepository ragDocuments;
  private final AiAdvisorClient llmClient;
  private final EmbeddingClient embeddingClient;
  private final ObjectMapper mapper;

  public DocumentGenerationService(TrackerEntryRepository trackerEntries,
                                   FoodEntryRepository foodEntries,
                                   FoodEntryItemRepository foodItems,
                                   RagDocumentRepository ragDocuments,
                                   AiAdvisorClient llmClient,
                                   EmbeddingClient embeddingClient,
                                   ObjectMapper mapper) {
    this.trackerEntries = trackerEntries;
    this.foodEntries = foodEntries;
    this.foodItems = foodItems;
    this.ragDocuments = ragDocuments;
    this.llmClient = llmClient;
    this.embeddingClient = embeddingClient;
    this.mapper = mapper;
  }

  /**
   * Ensure the user has up-to-date RAG documents.
   * Backfills missing reports for all past periods that have tracker data.
   */
  public void ensureUpToDate(Long userId) {
    try {
      backfillWeeklyReports(userId);
    } catch (Exception e) {
      log.warn("Failed to generate weekly report for user {}: {}", userId, e.getMessage());
    }
    try {
      backfillMonthlyReports(userId);
    } catch (Exception e) {
      log.warn("Failed to generate monthly report for user {}: {}", userId, e.getMessage());
    }
  }

  // ── Weekly reports ──

  private void backfillWeeklyReports(Long userId) {
    LocalDate today = LocalDate.now(ZoneOffset.UTC);
    LocalDate currentWeekEnd = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));

    // Collect already-covered weeks from existing reports
    List<RagDocument> existing = ragDocuments
        .findByUserIdAndDocTypeOrderByCreatedAtDesc(userId, "WEEKLY_REPORT");
    java.util.Set<String> covered = new java.util.HashSet<>();
    for (RagDocument doc : existing) {
      String ws = fieldFromMetadata(doc.getMetadata(), "weekStart");
      if (ws != null) covered.add(ws);
    }

    // Walk backwards up to 16 weeks, generating missing reports
    for (int i = 0; i < 16; i++) {
      LocalDate weekEnd = currentWeekEnd.minusWeeks(i);
      LocalDate weekStart = weekEnd.minusDays(6);
      if (covered.contains(weekStart.toString())) continue;

      Instant from = weekStart.atStartOfDay(ZoneOffset.UTC).toInstant();
      Instant to = weekEnd.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
      if (!hasTrackerData(userId, from, to)) continue;

      generateWeeklyReport(userId, from, to, weekStart, weekEnd);
    }
  }

  private void generateWeeklyReport(Long userId, Instant from, Instant to,
                                    LocalDate weekStart, LocalDate weekEnd) {
    String data = aggregateTrackerData(userId, from, to, weekStart, weekEnd, true);
    String summary = llmClient.reply(weeklyPrompt(data));
    float[] embedding = embeddingClient.embed(summary);

    Map<String, Object> meta = new LinkedHashMap<>();
    meta.put("coveredFrom", from.toString());
    meta.put("coveredTo", to.toString());
    meta.put("periodType", "week");
    meta.put("weekStart", weekStart.toString());
    meta.put("weekEnd", weekEnd.toString());

    String title = "Week of " + weekStart;
    try {
      ragDocuments.save(new RagDocument(userId, null, "WEEKLY_REPORT", title, summary,
          mapper.writeValueAsString(embeddingToList(embedding)),
          mapper.writeValueAsString(meta)));
      log.info("Generated WEEKLY_REPORT for user {}: {}", userId, title);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("Failed to serialize embedding", e);
    }
  }

  // ── Monthly reports ──

  private void backfillMonthlyReports(Long userId) {
    LocalDate today = LocalDate.now(ZoneOffset.UTC);

    // Collect already-covered months from existing reports
    List<RagDocument> existing = ragDocuments
        .findByUserIdAndDocTypeOrderByCreatedAtDesc(userId, "MONTHLY_REPORT");
    java.util.Set<String> covered = new java.util.HashSet<>();
    for (RagDocument doc : existing) {
      String y = fieldFromMetadata(doc.getMetadata(), "year");
      String m = fieldFromMetadata(doc.getMetadata(), "month");
      if (y != null && m != null) covered.add(y + "-" + m);
    }

    // Walk backwards up to 6 months, generating missing reports
    for (int i = 1; i <= 6; i++) {
      LocalDate monthStart = today.minusMonths(i).withDayOfMonth(1);
      LocalDate monthEnd = monthStart.withDayOfMonth(monthStart.lengthOfMonth());
      String key = monthStart.getYear() + "-" + monthStart.getMonth().toString();
      if (covered.contains(key)) continue;

      Instant from = monthStart.atStartOfDay(ZoneOffset.UTC).toInstant();
      Instant to = monthEnd.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
      if (!hasTrackerData(userId, from, to)) continue;

      generateMonthlyReport(userId, from, to, monthStart, monthEnd);
    }
  }

  private void generateMonthlyReport(Long userId, Instant from, Instant to,
                                     LocalDate monthStart, LocalDate monthEnd) {
    String data = aggregateTrackerData(userId, from, to, monthStart, monthEnd, false);
    String summary = llmClient.reply(monthlyPrompt(data));
    float[] embedding = embeddingClient.embed(summary);

    Map<String, Object> meta = new LinkedHashMap<>();
    meta.put("coveredFrom", from.toString());
    meta.put("coveredTo", to.toString());
    meta.put("periodType", "month");
    meta.put("month", monthStart.getMonth().toString());
    meta.put("year", monthStart.getYear());

    String title = monthStart.getMonth() + " " + monthStart.getYear();
    try {
      ragDocuments.save(new RagDocument(userId, null, "MONTHLY_REPORT", title, summary,
          mapper.writeValueAsString(embeddingToList(embedding)),
          mapper.writeValueAsString(meta)));
      log.info("Generated MONTHLY_REPORT for user {}: {}", userId, title);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("Failed to serialize embedding", e);
    }
  }

  private String fieldFromMetadata(String metadataJson, String field) {
    if (metadataJson == null) return null;
    try {
      JsonNode node = mapper.readTree(metadataJson);
      JsonNode value = node.get(field);
      return value != null ? value.asText() : null;
    } catch (Exception e) {
      return null;
    }
  }

  // ── Data helpers ──

  private boolean hasTrackerData(Long userId, Instant from, Instant to) {
    var page = trackerEntries.findOwned(userId, null, from, to, PageRequest.of(0, 1));
    return page.getTotalElements() > 0;
  }

  /**
   * Build a structured data string for the LLM with tracker stats + food intake for the period.
   */
  private String aggregateTrackerData(Long userId, Instant from, Instant to,
                                      LocalDate periodStart, LocalDate periodEnd,
                                      boolean includePriorComparison) {
    StringBuilder sb = new StringBuilder();
    sb.append("Period: ").append(periodStart).append(" to ").append(periodEnd).append("\n\n");

    // Tracker entries aggregation
    var page = trackerEntries.findOwned(userId, null, from, to,
        PageRequest.of(0, 200, org.springframework.data.domain.Sort.by("recordedAt")));
    List<TrackerEntry> entries = page.getContent();

    if (entries.isEmpty()) {
      sb.append("No tracker data for this period.\n");
    } else {
      Map<TrackerType, List<TrackerEntry>> grouped = entries.stream()
          .collect(Collectors.groupingBy(TrackerEntry::getTrackerType));

      sb.append("=== Tracker Data ===\n");
      for (var typeGroup : grouped.entrySet()) {
        TrackerType type = typeGroup.getKey();
        List<TrackerEntry> list = typeGroup.getValue();
        List<BigDecimal> amounts = list.stream().map(TrackerEntry::getAmount).toList();
        BigDecimal avg = average(amounts);
        BigDecimal min = amounts.stream().min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        BigDecimal max = amounts.stream().max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);

        sb.append(type.name()).append(": ")
            .append(list.size()).append(" entries, ")
            .append("avg=").append(avg).append(type.unit())
            .append(", min=").append(min).append(type.unit())
            .append(", max=").append(max).append(type.unit());

        // Notes (non-null)
        List<String> notable = list.stream()
            .filter(e -> e.getNotes() != null && !e.getNotes().isBlank())
            .map(e -> e.getRecordedAt() + ": " + e.getNotes())
            .toList();
        if (!notable.isEmpty()) {
          sb.append("\n  Notes: ").append(String.join("; ", notable));
        }
        sb.append("\n");
      }
    }

    // Food entries aggregation
    List<FoodEntry> foods = foodEntries
        .findByUserIdAndRecordedAtGreaterThanEqualAndRecordedAtLessThanOrderByRecordedAtDesc(
            userId, from, to);
    if (!foods.isEmpty()) {
      List<Long> entryIds = foods.stream().map(FoodEntry::getId).toList();
      List<FoodEntryItem> items = foodItems.findByFoodEntryIdInOrderById(entryIds);
      long days = foods.stream()
          .map(e -> e.getRecordedAt().atZone(ZoneOffset.UTC).toLocalDate())
          .distinct().count();
      days = Math.max(1, days);

      BigDecimal totalCal = BigDecimal.ZERO;
      BigDecimal totalPro = BigDecimal.ZERO;
      BigDecimal totalCarb = BigDecimal.ZERO;
      BigDecimal totalFat = BigDecimal.ZERO;
      BigDecimal totalFiber = BigDecimal.ZERO;
      for (FoodEntryItem item : items) {
        totalCal = totalCal.add(item.getCalories());
        totalPro = totalPro.add(item.getProteinGrams());
        totalCarb = totalCarb.add(item.getCarbohydrateGrams());
        totalFat = totalFat.add(item.getFatGrams());
        totalFiber = totalFiber.add(item.getFiberGrams());
      }

      sb.append("\n=== Food Intake ===\n");
      sb.append(foods.size()).append(" meals over ~").append(days).append(" days\n");
      sb.append("Daily averages: ")
          .append(totalCal.divide(BigDecimal.valueOf(days), 0, RoundingMode.HALF_UP))
          .append(" kcal, ")
          .append(totalPro.divide(BigDecimal.valueOf(days), 1, RoundingMode.HALF_UP))
          .append("g protein, ")
          .append(totalCarb.divide(BigDecimal.valueOf(days), 1, RoundingMode.HALF_UP))
          .append("g carbs, ")
          .append(totalFat.divide(BigDecimal.valueOf(days), 1, RoundingMode.HALF_UP))
          .append("g fat, ")
          .append(totalFiber.divide(BigDecimal.valueOf(days), 1, RoundingMode.HALF_UP))
          .append("g fiber\n");
    }

    // Prior period comparison (weekly only)
    if (includePriorComparison) {
      Instant priorFrom = from.minus(7, ChronoUnit.DAYS);
      Instant priorTo = from;
      var priorPage = trackerEntries.findOwned(userId, null, priorFrom, priorTo,
          PageRequest.of(0, 200, org.springframework.data.domain.Sort.by("recordedAt")));
      List<TrackerEntry> priorEntries = priorPage.getContent();

      if (!priorEntries.isEmpty()) {
        Map<TrackerType, List<TrackerEntry>> priorGrouped = priorEntries.stream()
            .collect(Collectors.groupingBy(TrackerEntry::getTrackerType));
        sb.append("\n=== Prior Week Comparison ===\n");
        for (var typeGroup : priorGrouped.entrySet()) {
          TrackerType type = typeGroup.getKey();
          List<BigDecimal> prior = typeGroup.getValue().stream().map(TrackerEntry::getAmount).toList();
          BigDecimal priorAvg = average(prior);
          sb.append(type.name()).append(" prior week: avg=")
              .append(priorAvg).append(type.unit())
              .append(" (").append(prior.size()).append(" entries)\n");
        }
      }
    }

    return sb.toString();
  }

  private BigDecimal average(List<BigDecimal> values) {
    if (values.isEmpty()) return BigDecimal.ZERO;
    BigDecimal sum = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    return sum.divide(BigDecimal.valueOf(values.size()), 2, RoundingMode.HALF_UP);
  }

  private List<Float> embeddingToList(float[] embedding) {
    List<Float> list = new ArrayList<>(embedding.length);
    for (float v : embedding) list.add(v);
    return list;
  }

  // ── Conversation summaries ──

  /**
   * Generate a summary document for a completed AI advisor conversation.
   * Skips if a summary was already generated for this session within the last 10 minutes.
   */
  public void generateConversationSummary(Long userId, Long sessionId,
                                          String question, String answer) {
    // 10-minute debounce
    Optional<RagDocument> recent = ragDocuments
        .findTopByUserIdAndDocTypeAndSessionIdOrderByCreatedAtDesc(
            userId, "CONVERSATION_SUMMARY", sessionId);
    if (recent.isPresent() && recent.get().getCreatedAt()
        .isAfter(Instant.now().minus(10, ChronoUnit.MINUTES))) {
      return;
    }

    String summary = llmClient.reply(conversationSummaryPrompt(question, answer));
    float[] embedding = embeddingClient.embed(summary);

    Map<String, Object> meta = new LinkedHashMap<>();
    meta.put("sessionId", sessionId);

    String title = truncate(question, 80);
    try {
      ragDocuments.save(new RagDocument(userId, sessionId, "CONVERSATION_SUMMARY",
          title, summary,
          mapper.writeValueAsString(embeddingToList(embedding)),
          mapper.writeValueAsString(meta)));
      log.info("Generated CONVERSATION_SUMMARY for session {}", sessionId);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("Failed to serialize embedding", e);
    }
  }

  private static String truncate(String text, int maxLen) {
    return text.length() <= maxLen ? text : text.substring(0, maxLen - 1) + "…";
  }

  private String conversationSummaryPrompt(String question, String answer) {
    return """
        Summarize the following wellness advisor conversation in 1-2 sentences.
        Focus on the key health topics discussed and any actionable advice given.

        User: %s
        Advisor: %s
        """.formatted(question, answer);
  }

  // ── LLM prompts ──

  private String weeklyPrompt(String data) {
    return """
        You are a wellness data analyst. Summarize the following week of user health data
        into a concise, natural-language report (3-5 sentences). Highlight notable trends,
        improvements, or concerns. Be supportive and encouraging. Do not diagnose.

        %s
        """.formatted(data);
  }

  private String monthlyPrompt(String data) {
    return """
        You are a wellness data analyst. Summarize the following month of user health data
        into a concise, natural-language report (4-6 sentences). Identify trends over the month,
        compare to any prior data shown, and note significant changes. Be supportive and factual.
        Do not diagnose.

        %s
        """.formatted(data);
  }
}
