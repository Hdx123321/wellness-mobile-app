package com.wellnessmate.advisor.service;

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
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiAdvisorService {
  private final AiChatSessionRepository sessions;
  private final AiChatMessageRepository messages;
  private final AiAdvisorClient client;
  private final OnboardingService onboarding;
  private final TrackerService trackers;

  public AiAdvisorService(AiChatSessionRepository sessions, AiChatMessageRepository messages,
                          AiAdvisorClient client, OnboardingService onboarding, TrackerService trackers) {
    this.sessions = sessions;
    this.messages = messages;
    this.client = client;
    this.onboarding = onboarding;
    this.trackers = trackers;
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
