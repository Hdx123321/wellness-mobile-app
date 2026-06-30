package com.wellnessmate.food.domain;

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

/** Compressed thumbnail retained only for confirmed AI photo meals. */
@Entity
@Table(name = "food_entry_photos")
public class FoodEntryPhoto {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true)
  private Long foodEntryId;

  @Column(nullable = false, length = 100)
  private String contentType;

  @Lob
  @Basic(fetch = FetchType.LAZY)
  @Column(nullable = false, columnDefinition = "LONGBLOB")
  private byte[] thumbnail;

  @Column(nullable = false)
  private Instant createdAt;

  protected FoodEntryPhoto() {
  }

  public FoodEntryPhoto(Long foodEntryId, String contentType, byte[] thumbnail) {
    this.foodEntryId = foodEntryId;
    this.contentType = contentType;
    this.thumbnail = thumbnail.clone();
    this.createdAt = Instant.now();
  }

  public Long getId() { return id; }
  public Long getFoodEntryId() { return foodEntryId; }
  public String getContentType() { return contentType; }
  public byte[] getThumbnail() { return thumbnail.clone(); }
}
