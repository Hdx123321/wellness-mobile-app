package com.wellnessmate.advisor.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wellnessmate.advisor.agent.AiAgentOrchestrator;
import com.wellnessmate.advisor.api.AiAdvisorMessageResponse;
import com.wellnessmate.advisor.api.AiChatSessionResponse;
import com.wellnessmate.advisor.domain.AiChatMessage;
import com.wellnessmate.advisor.domain.AiChatSession;
import com.wellnessmate.advisor.repository.AiChatMessageRepository;
import com.wellnessmate.advisor.repository.AiChatSessionRepository;
import com.wellnessmate.common.api.ApiException;
import com.wellnessmate.onboarding.api.ProfileResponse;
import com.wellnessmate.onboarding.service.OnboardingService;
import com.wellnessmate.tracker.domain.TrackerType;
import com.wellnessmate.tracker.repository.TrackerEntryRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class AiAdvisorService {
  private static final Logger log = LoggerFactory.getLogger(AiAdvisorService.class);

  private final AiChatSessionRepository sessions;
  private final AiChatMessageRepository messages;
  private final AiAdvisorClient client;
  private final AiAgentOrchestrator orchestrator;
  private final OnboardingService onboarding;
  private final TrackerEntryRepository trackerEntries;
  private final DocumentGenerationService docGen;
  private final RagRetrievalService ragRetrieval;
  private final ObjectMapper mapper;

  public AiAdvisorService(AiChatSessionRepository sessions, AiChatMessageRepository messages,
                           AiAdvisorClient client, AiAgentOrchestrator orchestrator,
                           OnboardingService onboarding,
                           TrackerEntryRepository trackerEntries,
                           DocumentGenerationService docGen,
                           RagRetrievalService ragRetrieval, ObjectMapper mapper) {
    this.sessions = sessions;
    this.messages = messages;
    this.client = client;
    this.orchestrator = orchestrator;
    this.onboarding = onboarding;
    this.trackerEntries = trackerEntries;
    this.docGen = docGen;
    this.ragRetrieval = ragRetrieval;
    this.mapper = mapper;
  }

  // ── Session management ──

  @Transactional(readOnly = true)
  public List<AiChatSessionResponse> listSessions(Long userId) {
    return sessions.findByUserIdOrderByUpdatedAtDesc(userId).stream()
        .map(AiChatSessionResponse::from).toList();
  }

  @Transactional
  public AiChatSessionResponse createSession(Long userId) {
    AiChatSession session = sessions.save(new AiChatSession(userId));
    return AiChatSessionResponse.from(session);
  }

  @Transactional
  public void deleteSession(Long userId, Long sessionId) {
    requireOwnership(userId, sessionId, "delete");
    messages.deleteBySessionId(sessionId);
    sessions.deleteByUserIdAndId(userId, sessionId);
  }

  @Transactional
  public AiChatSessionResponse renameSession(Long userId, Long sessionId, String title) {
    AiChatSession session = requireOwnership(userId, sessionId, "rename");
    session.setTitle(title.trim());
    sessions.save(session);
    return AiChatSessionResponse.from(session);
  }

  // ── Messages ──

  @Transactional(readOnly = true)
  public List<AiAdvisorMessageResponse> messagesForSession(Long userId, Long sessionId) {
    requireOwnership(userId, sessionId, "view");
    return messages.findTop100BySessionIdOrderByIdAsc(sessionId).stream()
        .map(AiAdvisorMessageResponse::from).toList();
  }

  // ── Streaming (agent-capable) ──

  @Transactional
  public SseEmitter sendStream(Long userId, Long sessionId, String content) {
    AiChatSession session = requireOwnership(userId, sessionId, "send to");
    Long id = session.getId();
    List<AiChatMessage> history = messages.findTop100BySessionIdOrderByIdAsc(id);

    // Ensure RAG documents are up to date (async — doesn't block current query)
    Thread.ofVirtual().start(() -> docGen.ensureUpToDate(userId));

    // Retrieve RAG context (sync — needed for this query)
    String ragContext = ragRetrieval.retrieve(userId, content.trim());

    // Build message list for the agent
    List<Map<String, Object>> msgList = buildAgentMessages(userId, history, content.trim(), ragContext);

    Instant now = Instant.now();
    boolean firstMessage = history.isEmpty();

    // Save user message
    messages.save(new AiChatMessage(id, "USER", content.trim(), now));

    SseEmitter emitter = new SseEmitter(300_000L); // 5 min for multi-round agent

    // Run orchestrator on a virtual thread to avoid blocking the Tomcat thread
    Thread.ofVirtual().start(() -> {
      orchestrator.run(userId, msgList, hasConcreteEntryId(content.toLowerCase()), emitter,
        // onAnswer — persist and send "done"
        answerText -> {
          try {
            AiChatMessage reply = messages.save(
                new AiChatMessage(id, "ASSISTANT", answerText, Instant.now()));
            session.touch(Instant.now());
            if (firstMessage) session.setTitle(truncate(content, 50));
            sessions.save(session);

            // Generate conversation summary (fire-and-forget, best-effort)
            try {
              docGen.generateConversationSummary(userId, id, content.trim(), answerText);
            } catch (Exception summaryError) {
              log.warn("Conversation summary generation failed: {}", summaryError.getMessage());
            }

            // Send final "done" event
            Map<String, Object> done = new LinkedHashMap<>();
            done.put("type", "done");
            done.put("messageId", reply.getId());
            done.put("createdAt", reply.getCreatedAt().toString());
            try {
              emitter.send(SseEmitter.event().data(mapper.writeValueAsString(done)));
            } catch (IllegalStateException e) {
              log.debug("SSE emitter already completed (client likely disconnected)");
            }
            emitter.complete();
          } catch (Exception e) {
            log.error("Failed to persist streamed reply", e);
            emitter.completeWithError(e);
          }
        },
        // onError
        error -> {
          log.error("Agent orchestration failed", error);
          try {
            emitter.completeWithError(error);
          } catch (IllegalStateException ignored) {
            log.debug("Emitter already completed");
          }
        });
      });

    return emitter;
  }

  // ── Helpers ──

  private AiChatSession requireOwnership(Long userId, Long sessionId, String action) {
    AiChatSession session = sessions.findById(sessionId)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND", "Session not found"));
    if (!session.getUserId().equals(userId))
      throw new ApiException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Cannot " + action + " another user's session");
    return session;
  }

  // ── Agent message builder ──

  /**
   * Build the initial message list for the agent chat.
   * Structure: system → profile context → RAG context → conversation history → user question.
   */
  private List<Map<String, Object>> buildAgentMessages(Long userId,
                                                        List<AiChatMessage> history,
                                                        String question,
                                                        String ragContext) {
    var list = new ArrayList<Map<String, Object>>();

    // 1. System prompt
    list.add(Map.of("role", "system", "content", (Object) AiAdvisorClient.AGENT_SYSTEM_PROMPT));
    String toolRequirement = trackerToolRequirement(question);
    if (!toolRequirement.isBlank()) {
      list.add(Map.of("role", "system", "content", (Object) toolRequirement));
    }
    String mutationRequirement = trackerMutationRequirement(question);
    if (!mutationRequirement.isBlank()) {
      list.add(Map.of("role", "system", "content", (Object) mutationRequirement));
    }
    String vagueMutationRequirement = vagueTrackerMutationRequirement(question);
    if (!vagueMutationRequirement.isBlank()) {
      list.add(Map.of("role", "system", "content", (Object) vagueMutationRequirement));
    }
    String profileRequirement = profileUpdateRequirement(question);
    if (!profileRequirement.isBlank()) {
      list.add(Map.of("role", "system", "content", (Object) profileRequirement));
    }
    String profileReadRequirement = profileReadRequirement(question);
    if (!profileReadRequirement.isBlank()) {
      list.add(Map.of("role", "system", "content", (Object) profileReadRequirement));
    }

    // 2. Profile context
    ProfileResponse profile;
    try { profile = onboarding.get(userId); } catch (Exception e) { profile = null; }
    String profileCtx = profile != null
        ? "heightCm=%s, latestWeightKg=%s, startingWeightKg=%s, targetWeightKg=%s, activityLevel=%s"
            .formatted(profile.heightCm(), latestWeightKg(userId), profile.currentWeightKg(),
                profile.targetWeightKg(), profile.activityLevel())
        : "No profile yet.";
    list.add(Map.of("role", "system", "content", (Object)
        ("Authoritative current user profile from the database: " + profileCtx
            + ". This current profile overrides older conversation history.")));

    // 3. RAG context (semantic + structured retrieval)
    if (ragContext != null && !ragContext.isBlank()) {
      list.add(Map.of("role", "user", "content", (Object) ragContext));
    }

    // 4. Recent conversation history (last 12 messages, USER/ASSISTANT only)
    var recent = history.stream()
        .skip(Math.max(0, history.size() - 12L))
        .toList();
    Map<String, String> roleMap = Map.of("USER", "user", "ASSISTANT", "assistant");
    for (AiChatMessage msg : recent) {
      String role = roleMap.getOrDefault(msg.getRole(), msg.getRole().toLowerCase());
      list.add(Map.of("role", role, "content", (Object) msg.getContent()));
    }

    // 5. Current user question
    list.add(Map.of("role", "user", "content", (Object) question));

    return list;
  }

  private String trackerToolRequirement(String question) {
    String q = question == null ? "" : question.toLowerCase();
    List<String> trackerTypes = new ArrayList<>();
    addIfMentioned(trackerTypes, q, "WEIGHT", "weight", "kg", "body mass", "体重");
    addIfMentioned(trackerTypes, q, "SLEEP", "sleep", "bed", "recovery", "睡眠", "恢复");
    addIfMentioned(trackerTypes, q, "STEPS", "steps", "step count", "walk", "步数");
    addIfMentioned(trackerTypes, q, "WORKOUT", "workout", "training", "exercise", "lifting",
        "strength", "cardio", "run", "cycle", "gym", "训练", "锻炼");
    addIfMentioned(trackerTypes, q, "WATER", "water", "hydration", "hydrate", "喝水", "水");
    addIfMentioned(trackerTypes, q, "FOOD", "food", "meal", "nutrition", "protein", "calorie",
        "carb", "fat", "diet", "饭", "蛋白", "热量");
    addIfMentioned(trackerTypes, q, "MEDICINE", "medicine", "medication", "dose", "药");

    boolean dataQuestion = containsAny(q, "average", "avg", "latest", "current", "count", "how many",
        "how much", "trend", "over the last", "last 7", "last 30", "last three", "recent",
        "recently", "specific", "during", "between", "from", "to", "what happened", "compare",
        "数据", "平均", "最近", "趋势", "多少", "几次");
    if (trackerTypes.isEmpty() || !dataQuestion) {
      return "";
    }
    return """
        This user question asks for tracker-backed data. Before giving the final answer,
        you MUST call query_tracker_data for these tracker types: %s.
        Use a look-back window that covers the requested period; use 90 days for three-month
        or April/May/June comparisons, 30 days for recent monthly questions, and 7 days for
        current-week questions. Do not answer from RAG summaries alone.
        """.formatted(String.join(", ", trackerTypes));
  }

  private String latestWeightKg(Long userId) {
    return trackerEntries
        .findFirstByUserIdAndTrackerTypeOrderByRecordedAtDescIdDesc(userId, TrackerType.WEIGHT)
        .map(entry -> entry.getAmount().toString())
        .orElse("none");
  }

  private String trackerMutationRequirement(String question) {
    String q = question == null ? "" : question.toLowerCase();
    boolean hasEntryId = hasConcreteEntryId(q);
    boolean wantsUpdate = containsAny(q, "change", "update", "fix", "edit", "correct",
        "set", "modify", "改", "修改", "更正");
    boolean wantsDelete = containsAny(q, "delete", "remove", "erase", "drop", "删除", "移除");
    if (!hasEntryId || (!wantsUpdate && !wantsDelete)) {
      return "";
    }
    String tool = wantsDelete ? "delete_tracker_entry" : "update_tracker_entry";
    return """
        This is an explicit tracker mutation request with a concrete entry ID.
        You MUST call %s now to trigger the backend confirmation gate.
        Do not stop after querying or merely ask "shall I proceed"; the backend will prevent
        the data change and return the exact confirmation token the user must provide.
        """.formatted(tool);
  }

  private String vagueTrackerMutationRequirement(String question) {
    String q = question == null ? "" : question.toLowerCase();
    boolean wantsUpdate = containsAny(q, "change", "update", "fix", "edit", "correct",
        "set", "modify", "改", "修改", "更正");
    boolean wantsDelete = containsAny(q, "delete", "remove", "erase", "drop", "删除", "移除");
    if ((!wantsUpdate && !wantsDelete) || hasConcreteEntryId(q)) {
      return "";
    }
    return """
        This is a vague tracker mutation request with no concrete entry ID in the user's
        current message. You may call query_tracker_data to find candidates, but you MUST NOT
        call update_tracker_entry or delete_tracker_entry in this turn. End by asking the user
        to choose the exact entry ID and desired change/delete action.
        """;
  }

  private String profileUpdateRequirement(String question) {
    String q = question == null ? "" : question.toLowerCase();
    boolean wantsUpdate = containsAny(q, "change", "update", "fix", "edit", "correct",
        "set", "modify", "改", "修改", "更正");
    boolean profileField = containsAny(q, "height", "profile", "target weight", "goal weight",
        "current weight", "activity level", "daily routine", "身高", "资料", "目标体重");
    if (!wantsUpdate || !profileField) {
      return "";
    }
    return """
        This user is asking to update a profile field, not a tracker entry.
        You MUST call update_profile for supported fields such as height_cm,
        current_weight_kg, target_weight_kg, goal_duration_weeks, daily_routine,
        or activity_level. Do not tell the user profile updates are unavailable.
        """;
  }

  private String profileReadRequirement(String question) {
    String q = question == null ? "" : question.toLowerCase();
    boolean asksCurrent = containsAny(q, "what is", "what's", "whats", "tell me", "show me",
        "current", "now", "多少", "是什么");
    boolean profileField = containsAny(q, "height", "profile", "target weight", "goal weight",
        "current weight", "activity level", "daily routine", "身高", "资料", "目标体重");
    if (!asksCurrent || !profileField) {
      return "";
    }
    return """
        This user is asking for a current profile fact. You MUST call query_profile before
        answering. The profile may have changed outside the chat, so do not rely on recent
        conversation history.
        """;
  }

  private boolean hasConcreteEntryId(String q) {
    return q.matches("(?s).*\\b(entry|tracker entry)\\s*#?\\s*\\d+.*")
        || q.matches("(?s).*#\\s*\\d+.*");
  }

  private void addIfMentioned(List<String> types, String question, String type, String... needles) {
    for (String needle : needles) {
      if (question.contains(needle)) {
        if (!types.contains(type)) types.add(type);
        return;
      }
    }
  }

  private boolean containsAny(String text, String... needles) {
    for (String needle : needles) {
      if (text.contains(needle)) return true;
    }
    return false;
  }

  private static String truncate(String text, int maxLen) {
    return text.length() <= maxLen ? text : text.substring(0, maxLen - 1) + "…";
  }
}
