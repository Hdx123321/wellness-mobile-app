CREATE TABLE users (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  username VARCHAR(50) NOT NULL,
  email VARCHAR(255) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  display_name VARCHAR(100),
  created_at TIMESTAMP(6) NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL,
  CONSTRAINT uk_users_username UNIQUE (username),
  CONSTRAINT uk_users_email UNIQUE (email)
);

CREATE TABLE wellness_records (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  record_date DATE NOT NULL,
  sleep_hours DECIMAL(4, 2),
  exercise_type VARCHAR(100),
  exercise_minutes INT,
  water_ml INT,
  mood VARCHAR(30),
  notes VARCHAR(1000),
  created_at TIMESTAMP(6) NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT fk_wellness_records_user FOREIGN KEY (user_id) REFERENCES users (id),
  CONSTRAINT uk_wellness_records_user_date UNIQUE (user_id, record_date),
  CONSTRAINT ck_wellness_records_sleep CHECK (sleep_hours IS NULL OR (sleep_hours >= 0 AND sleep_hours <= 24)),
  CONSTRAINT ck_wellness_records_exercise CHECK (exercise_minutes IS NULL OR exercise_minutes >= 0),
  CONSTRAINT ck_wellness_records_water CHECK (water_ml IS NULL OR water_ml >= 0)
);

CREATE INDEX idx_wellness_records_user_date
  ON wellness_records (user_id, record_date DESC);

CREATE TABLE chat_sessions (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  title VARCHAR(150),
  created_at TIMESTAMP(6) NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL,
  CONSTRAINT fk_chat_sessions_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX idx_chat_sessions_user_updated
  ON chat_sessions (user_id, updated_at DESC);

CREATE TABLE chat_messages (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  session_id BIGINT NOT NULL,
  role VARCHAR(20) NOT NULL,
  content TEXT NOT NULL,
  created_at TIMESTAMP(6) NOT NULL,
  CONSTRAINT fk_chat_messages_session FOREIGN KEY (session_id) REFERENCES chat_sessions (id) ON DELETE CASCADE,
  CONSTRAINT ck_chat_messages_role CHECK (role IN ('USER', 'ASSISTANT', 'SYSTEM'))
);

CREATE INDEX idx_chat_messages_session_created
  ON chat_messages (session_id, created_at);

CREATE TABLE recommendations (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  summary VARCHAR(255) NOT NULL,
  content TEXT NOT NULL,
  source VARCHAR(30) NOT NULL,
  period_start DATE,
  period_end DATE,
  created_at TIMESTAMP(6) NOT NULL,
  CONSTRAINT fk_recommendations_user FOREIGN KEY (user_id) REFERENCES users (id),
  CONSTRAINT ck_recommendations_source CHECK (source IN ('ON_DEMAND', 'SCHEDULED'))
);

CREATE INDEX idx_recommendations_user_created
  ON recommendations (user_id, created_at DESC);

