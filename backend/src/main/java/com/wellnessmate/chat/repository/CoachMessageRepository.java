package com.wellnessmate.chat.repository;

import com.wellnessmate.chat.domain.CoachMessage;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CoachMessageRepository extends JpaRepository<CoachMessage, Long> {
  List<CoachMessage> findTop100ByConversationIdAndIdGreaterThanOrderByIdAsc(Long conversationId, Long afterId);
  Optional<CoachMessage> findFirstByConversationIdOrderByIdDesc(Long conversationId);
}
