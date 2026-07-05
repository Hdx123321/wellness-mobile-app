package com.wellnessmate.advisor.repository;

import com.wellnessmate.advisor.domain.AiChatSession;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiChatSessionRepository extends JpaRepository<AiChatSession, Long> {
  List<AiChatSession> findByUserIdOrderByUpdatedAtDesc(Long userId);
  void deleteByUserIdAndId(Long userId, Long id);
}
