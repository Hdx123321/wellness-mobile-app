CREATE TABLE uploaded_files (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  content_type VARCHAR(100) NOT NULL,
  filename VARCHAR(255) NOT NULL,
  data LONGBLOB NOT NULL,
  created_at TIMESTAMP(6) NOT NULL,
  CONSTRAINT fk_uploaded_file_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
