package com.wellnessmate.advisor.repository;

import com.wellnessmate.advisor.domain.AiChatMessage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiChatMessageRepository extends JpaRepository<AiChatMessage, Long> {
  List<AiChatMessage> findTop100BySessionIdOrderByIdAsc(Long sessionId);
}
