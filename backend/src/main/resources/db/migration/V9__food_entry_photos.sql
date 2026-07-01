CREATE TABLE food_entry_photos (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  food_entry_id BIGINT NOT NULL,
  content_type VARCHAR(100) NOT NULL,
  thumbnail LONGBLOB NOT NULL,
  created_at TIMESTAMP(6) NOT NULL,
  CONSTRAINT uk_food_entry_photos_entry UNIQUE (food_entry_id),
  CONSTRAINT fk_food_entry_photos_entry FOREIGN KEY (food_entry_id)
    REFERENCES food_entries (id) ON DELETE CASCADE
);
