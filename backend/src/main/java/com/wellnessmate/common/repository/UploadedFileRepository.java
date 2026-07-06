package com.wellnessmate.common.repository;

import com.wellnessmate.common.domain.UploadedFile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UploadedFileRepository extends JpaRepository<UploadedFile, Long> {}
