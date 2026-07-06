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
import com.wellnessmate.tracker.service.TrackerService;
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
  private final TrackerService trackers;
  private final DocumentGenerationService docGen;
  private final RagRetrievalService ragRetrieval;
  private final ObjectMapper mapper;

  public AiAdvisorService(AiChatSessionRepository sessions, AiChatMessageRepository messages,
                          AiAdvisorClient client, AiAgentOrchestrator orchestrator,
                          OnboardingService onboarding,
                          TrackerService trackers, DocumentGenerationService docGen,
                          RagRetrievalService ragRetrieval, ObjectMapper mapper) {
    this.sessions = sessions;
    this.messages = messages;
    this.client = client;
    this.orchestrator = orchestrator;
    this.onboarding = onboarding;
    this.trackers = trackers;
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
      orchestrator.run(userId, msgList, emitter,
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

    // 2. Profile context
    ProfileResponse profile;
    try { profile = onboarding.get(userId); } catch (Exception e) { profile = null; }
    String profileCtx = profile != null
        ? "heightCm=%s, currentWeightKg=%s, targetWeightKg=%s, activityLevel=%s"
            .formatted(profile.heightCm(), profile.currentWeightKg(),
                profile.targetWeightKg(), profile.activityLevel())
        : "No profile yet.";
    list.add(Map.of("role", "user", "content", (Object) ("User profile: " + profileCtx)));

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

  private static String truncate(String text, int maxLen) {
    return text.length() <= maxLen ? text : text.substring(0, maxLen - 1) + "…";
  }
}
