package com.wellnessmate.advisor.repository;

import com.wellnessmate.advisor.domain.RagDocument;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RagDocumentRepository extends JpaRepository<RagDocument, Long> {

  List<RagDocument> findByUserIdOrderByCreatedAtDesc(Long userId);

  List<RagDocument> findTop80ByUserIdOrderByCreatedAtDesc(Long userId);

  List<RagDocument> findByUserIdAndDocTypeOrderByCreatedAtDesc(Long userId, String docType);

  Optional<RagDocument> findTopByUserIdAndDocTypeOrderByCreatedAtDesc(Long userId, String docType);

  List<RagDocument> findTop5ByUserIdOrderByCreatedAtDesc(Long userId);

  Optional<RagDocument> findTopByUserIdAndDocTypeAndSessionIdOrderByCreatedAtDesc(
      Long userId, String docType, Long sessionId);
}
