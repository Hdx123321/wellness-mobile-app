package com.wellnessmate.common.domain;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "uploaded_files")
public class UploadedFile {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
  @Column(nullable = false) private Long userId;
  @Column(nullable = false, length = 100) private String contentType;
  @Column(nullable = false, length = 255) private String filename;
  @Lob @Basic(fetch = FetchType.LAZY)
  @Column(nullable = false, columnDefinition = "LONGBLOB") private byte[] data;
  @Column(nullable = false) private Instant createdAt;

  protected UploadedFile() {}

  public UploadedFile(Long userId, String contentType, String filename, byte[] data) {
    this.userId = userId;
    this.contentType = contentType;
    this.filename = filename;
    this.data = data.clone();
    this.createdAt = Instant.now();
  }

  public Long getId() { return id; }
  public String getContentType() { return contentType; }
  public byte[] getData() { return data.clone(); }
}
