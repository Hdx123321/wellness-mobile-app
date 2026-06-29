package com.wellnessmate.advisor.repository;

import com.wellnessmate.advisor.domain.AiChatSession;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiChatSessionRepository extends JpaRepository<AiChatSession, Long> {
  Optional<AiChatSession> findFirstByUserIdOrderByUpdatedAtDesc(Long userId);
}
