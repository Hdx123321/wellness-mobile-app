CREATE TABLE tracker_entries (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  tracker_type VARCHAR(30) NOT NULL,
  recorded_at TIMESTAMP(6) NOT NULL,
  amount DECIMAL(12, 2) NOT NULL,
  detail VARCHAR(255),
  notes VARCHAR(1000),
  source VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
  created_at TIMESTAMP(6) NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT fk_tracker_entries_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
  CONSTRAINT ck_tracker_entries_type CHECK (tracker_type IN ('FOOD', 'WEIGHT', 'WORKOUT', 'STEPS', 'SLEEP', 'WATER')),
  CONSTRAINT ck_tracker_entries_amount CHECK (amount >= 0)
);

CREATE INDEX idx_tracker_entries_user_recorded
  ON tracker_entries (user_id, recorded_at DESC);

CREATE INDEX idx_tracker_entries_user_type_recorded
  ON tracker_entries (user_id, tracker_type, recorded_at DESC);
