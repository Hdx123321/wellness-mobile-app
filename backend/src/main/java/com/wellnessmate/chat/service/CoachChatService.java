package com.wellnessmate.chat.service;

import com.wellnessmate.auth.domain.UserAccount;
import com.wellnessmate.auth.domain.UserRole;
import com.wellnessmate.auth.repository.UserAccountRepository;
import com.wellnessmate.chat.api.CoachConversationResponse;
import com.wellnessmate.chat.api.CoachMessageResponse;
import com.wellnessmate.chat.domain.CoachConversation;
import com.wellnessmate.chat.domain.CoachMessage;
import com.wellnessmate.chat.repository.CoachConversationRepository;
import com.wellnessmate.chat.repository.CoachMessageRepository;
import com.wellnessmate.common.api.ApiException;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CoachChatService {
  private final CoachConversationRepository conversations;
  private final CoachMessageRepository messages;
  private final UserAccountRepository users;

  public CoachChatService(CoachConversationRepository conversations, CoachMessageRepository messages,
                          UserAccountRepository users) {
    this.conversations = conversations;
    this.messages = messages;
    this.users = users;
  }

  @Transactional
  public List<CoachConversationResponse> conversations(Long userId) {
    UserAccount user = requireUser(userId);
    if (user.getRole() == UserRole.CLIENT) {
      CoachConversation conversation = conversations.findByClientId(userId)
          .orElseGet(() -> assignCoach(userId));
      return conversation == null ? List.of() : List.of(toResponse(conversation));
    }
    return conversations.findByCoachIdOrderByUpdatedAtDesc(userId).stream().map(this::toResponse).toList();
  }

  @Transactional(readOnly = true)
  public List<CoachMessageResponse> messages(Long userId, Long conversationId, Long afterId) {
    CoachConversation conversation = requireParticipant(userId, conversationId);
    return messages.findTop100ByConversationIdAndIdGreaterThanOrderByIdAsc(
        conversation.getId(), Math.max(0, afterId)).stream().map(this::toResponse).toList();
  }

  @Transactional
  public CoachMessageResponse send(Long userId, Long conversationId, String content) {
    CoachConversation conversation = requireParticipant(userId, conversationId);
    Instant now = Instant.now();
    CoachMessage saved = messages.save(new CoachMessage(
        conversation.getId(), userId, content.trim(), now));
    conversation.touch(now);
    return toResponse(saved);
  }

  private CoachConversation assignCoach(Long clientId) {
    return users.findFirstByRoleOrderByIdAsc(UserRole.COACH)
        .map(coach -> conversations.save(new CoachConversation(clientId, coach.getId())))
        .orElse(null);
  }

  private CoachConversation requireParticipant(Long userId, Long id) {
    CoachConversation conversation = conversations.findById(id)
        .orElseThrow(() -> notFound());
    if (!conversation.getClientId().equals(userId) && !conversation.getCoachId().equals(userId)) {
      throw notFound();
    }
    return conversation;
  }

  private CoachConversationResponse toResponse(CoachConversation conversation) {
    String last = messages.findFirstByConversationIdOrderByIdDesc(conversation.getId())
        .map(CoachMessage::getContent).orElse(null);
    return new CoachConversationResponse(conversation.getId(), name(conversation.getClientId()),
        name(conversation.getCoachId()), last, conversation.getUpdatedAt());
  }

  private CoachMessageResponse toResponse(CoachMessage message) {
    UserAccount sender = requireUser(message.getSenderId());
    return new CoachMessageResponse(message.getId(), sender.getId(), name(sender),
        sender.getRole().name(), message.getContent(), message.getCreatedAt());
  }

  private String name(Long userId) { return name(requireUser(userId)); }

  private String name(UserAccount user) {
    return user.getDisplayName() == null ? user.getUsername() : user.getDisplayName();
  }

  private UserAccount requireUser(Long userId) {
    return users.findById(userId).orElseThrow(() -> new ApiException(
        HttpStatus.UNAUTHORIZED, "USER_NOT_FOUND", "User not found"));
  }

  private ApiException notFound() {
    return new ApiException(HttpStatus.NOT_FOUND, "COACH_CONVERSATION_NOT_FOUND",
        "Coach conversation not found");
  }
}
