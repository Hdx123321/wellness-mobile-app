package com.wellnessmate.advisor.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wellnessmate.advisor.api.AiAdvisorMessageResponse;
import com.wellnessmate.advisor.domain.AiChatMessage;
import com.wellnessmate.advisor.domain.AiChatSession;
import com.wellnessmate.advisor.repository.AiChatMessageRepository;
import com.wellnessmate.advisor.repository.AiChatSessionRepository;
import com.wellnessmate.onboarding.api.ProfileResponse;
import com.wellnessmate.onboarding.service.OnboardingService;
import com.wellnessmate.tracker.api.TrackerEntryResponse;
import com.wellnessmate.tracker.service.TrackerService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class AiAdvisorService {
  private static final Logger log = LoggerFactory.getLogger(AiAdvisorService.class);

  private final AiChatSessionRepository sessions;
  private final AiChatMessageRepository messages;
  private final AiAdvisorClient client;
  private final OnboardingService onboarding;
  private final TrackerService trackers;
  private final ObjectMapper mapper;

  public AiAdvisorService(AiChatSessionRepository sessions, AiChatMessageRepository messages,
                          AiAdvisorClient client, OnboardingService onboarding,
                          TrackerService trackers, ObjectMapper mapper) {
    this.sessions = sessions;
    this.messages = messages;
    this.client = client;
    this.onboarding = onboarding;
    this.trackers = trackers;
    this.mapper = mapper;
  }

  @Transactional(readOnly = true)
  public List<AiAdvisorMessageResponse> messages(Long userId) {
    return sessions.findFirstByUserIdOrderByUpdatedAtDesc(userId)
        .map(session -> messages.findTop100BySessionIdOrderByIdAsc(session.getId()).stream()
            .map(AiAdvisorMessageResponse::from).toList())
        .orElse(List.of());
  }

  public AiAdvisorMessageResponse send(Long userId, String content) {
    AiChatSession session = sessions.findFirstByUserIdOrderByUpdatedAtDesc(userId)
        .orElseGet(() -> sessions.save(new AiChatSession(userId)));
    List<AiChatMessage> history = messages.findTop100BySessionIdOrderByIdAsc(session.getId());
    String answer = client.reply(prompt(userId, history, content.trim()));
    Instant now = Instant.now();
    messages.save(new AiChatMessage(session.getId(), "USER", content.trim(), now));
    AiChatMessage reply = messages.save(new AiChatMessage(
        session.getId(), "ASSISTANT", answer, now.plusMillis(1)));
    session.touch(now);
    sessions.save(session);
    return AiAdvisorMessageResponse.from(reply);
  }

  @Transactional
  public SseEmitter sendStream(Long userId, String content) {
    AiChatSession session = sessions.findFirstByUserIdOrderByUpdatedAtDesc(userId)
        .orElseGet(() -> sessions.save(new AiChatSession(userId)));
    List<AiChatMessage> history = messages.findTop100BySessionIdOrderByIdAsc(session.getId());
    String promptText = prompt(userId, history, content.trim());
    Instant now = Instant.now();

    // Save user message immediately
    messages.save(new AiChatMessage(session.getId(), "USER", content.trim(), now));

    SseEmitter emitter = new SseEmitter(120_000L);
    StringBuilder fullAnswer = new StringBuilder();

    client.replyStream(promptText,
        // onToken: forward each token to the client
        token -> {
          try {
            fullAnswer.append(token);
            emitter.send(SseEmitter.event().data(token));
          } catch (Exception e) {
            log.warn("Failed to send token to emitter", e);
          }
        },
        // onError: propagate to client
        error -> emitter.completeWithError(error),
        // onComplete: persist AI message and send done event
        () -> {
          try {
            if (!fullAnswer.isEmpty()) {
              AiChatMessage reply = messages.save(new AiChatMessage(
                  session.getId(), "ASSISTANT", fullAnswer.toString(), Instant.now()));
              session.touch(Instant.now());
              sessions.save(session);
              Map<String, Object> done = new LinkedHashMap<>();
              done.put("type", "done");
              done.put("messageId", reply.getId());
              done.put("createdAt", reply.getCreatedAt().toString());
              emitter.send(SseEmitter.event().data(mapper.writeValueAsString(done)));
            }
            emitter.complete();
          } catch (Exception e) {
            log.error("Failed to persist streamed reply", e);
            emitter.completeWithError(e);
          }
        });

    return emitter;
  }

  private String prompt(Long userId, List<AiChatMessage> history, String question) {
    ProfileResponse profile = onboarding.get(userId);
    List<TrackerEntryResponse> recent = trackers.list(userId, null,
        Instant.now().minus(7, ChronoUnit.DAYS), Instant.now(), 0, 50).content();
    String conversation = history.stream().skip(Math.max(0, history.size() - 12L))
        .map(message -> message.getRole() + ": " + message.getContent())
        .reduce("", (left, right) -> left + "\n" + right);
    return """
        Private context:
        heightCm=%s, profileWeightKg=%s, targetWeightKg=%s, activityLevel=%s
        Recent tracker entries: %s
        Recent conversation: %s
        USER: %s
        """.formatted(profile.heightCm(), profile.currentWeightKg(), profile.targetWeightKg(),
        profile.activityLevel(), recent, conversation, question);
  }
}
