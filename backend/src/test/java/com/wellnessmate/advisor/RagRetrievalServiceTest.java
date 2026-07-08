package com.wellnessmate.advisor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wellnessmate.advisor.domain.RagDocument;
import com.wellnessmate.advisor.repository.RagDocumentRepository;
import com.wellnessmate.advisor.service.EmbeddingClient;
import com.wellnessmate.advisor.service.RagRetrievalService;
import com.wellnessmate.food.repository.FoodEntryItemRepository;
import com.wellnessmate.food.repository.FoodEntryRepository;
import com.wellnessmate.tracker.domain.TrackerType;
import com.wellnessmate.tracker.repository.TrackerEntryRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;

class RagRetrievalServiceTest {

  private final RagDocumentRepository ragDocuments = org.mockito.Mockito.mock(RagDocumentRepository.class);
  private final TrackerEntryRepository trackerEntries = org.mockito.Mockito.mock(TrackerEntryRepository.class);
  private final FoodEntryRepository foodEntries = org.mockito.Mockito.mock(FoodEntryRepository.class);
  private final FoodEntryItemRepository foodItems = org.mockito.Mockito.mock(FoodEntryItemRepository.class);
  private final EmbeddingClient embeddingClient = org.mockito.Mockito.mock(EmbeddingClient.class);
  private RagRetrievalService service;

  @BeforeEach
  void setUp() {
    service = new RagRetrievalService(ragDocuments, trackerEntries, foodEntries, foodItems,
        embeddingClient, new ObjectMapper());
    when(trackerEntries.findOwned(eq(1L), any(), any(), any(), any()))
        .thenReturn(new PageImpl<>(List.of()));
    when(foodEntries.findByUserIdAndRecordedAtGreaterThanEqualAndRecordedAtLessThanOrderByRecordedAtDesc(
        eq(1L), any(), any())).thenReturn(List.of());
    when(embeddingClient.embed(any())).thenReturn(new float[] {1f, 0f});
  }

  @Test
  void recentQuestionsPreferWeeklyReportsOverConversationMemory() {
    when(ragDocuments.findTop80ByUserIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(
        doc("CONVERSATION_SUMMARY", "Old chat", "We discussed a vacation meal.",
            "{\"sessionId\":9}"),
        doc("WEEKLY_REPORT", "Week of 2026-07-01", "Sleep averaged 7 hours.",
            "{\"weekStart\":\"2026-07-01\",\"weekEnd\":\"2026-07-07\"}")));

    String context = service.retrieve(1L, "How has my sleep been recently?");

    assertThat(context).contains("retrieval_intent=RECENT", "doc_type=WEEKLY_REPORT",
        "weekStart=2026-07-01", "Sleep averaged 7 hours.");
    assertThat(context).doesNotContain("CONVERSATION_SUMMARY", "vacation meal");
  }

  @Test
  void conversationQuestionsOnlyRecallConversationSummaries() {
    when(ragDocuments.findTop80ByUserIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(
        doc("MONTHLY_REPORT", "June 2026", "Weight trended down.",
            "{\"month\":\"JUNE\",\"year\":2026}"),
        doc("CONVERSATION_SUMMARY", "Protein chat", "We discussed increasing protein.",
            "{\"sessionId\":4}")));

    String context = service.retrieve(1L, "What did we discuss before about protein?");

    assertThat(context).contains("retrieval_intent=CONVERSATION_MEMORY",
        "doc_type=CONVERSATION_SUMMARY", "sessionId=4", "increasing protein");
    assertThat(context).doesNotContain("MONTHLY_REPORT", "Weight trended down");
  }

  @Test
  void actionRequestsSkipSemanticMemory() {
    String context = service.retrieve(1L, "Log 500 ml water for today");

    assertThat(context).contains("retrieval_intent=ACTION");
    verify(embeddingClient, never()).embed(any());
    verify(ragDocuments, never()).findTop80ByUserIdOrderByCreatedAtDesc(any());
  }

  private RagDocument doc(String type, String title, String content, String metadata) {
    return new RagDocument(1L, null, type, title, content, "[1.0,0.0]", metadata);
  }
}
