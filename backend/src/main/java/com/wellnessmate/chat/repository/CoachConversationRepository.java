package com.wellnessmate.chat.repository;

import com.wellnessmate.chat.domain.CoachConversation;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CoachConversationRepository extends JpaRepository<CoachConversation, Long> {
  Optional<CoachConversation> findByClientId(Long clientId);
  List<CoachConversation> findByCoachIdOrderByUpdatedAtDesc(Long coachId);
}
