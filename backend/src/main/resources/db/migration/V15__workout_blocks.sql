CREATE TABLE workout_blocks (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  plan_id BIGINT NOT NULL,
  sort_order INT NOT NULL DEFAULT 0,
  title VARCHAR(200) NOT NULL,
  content TEXT,
  image_url VARCHAR(500),
  video_url VARCHAR(500),
  created_at TIMESTAMP(6) NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL,
  CONSTRAINT fk_workout_block_plan FOREIGN KEY (plan_id) REFERENCES training_plans (id) ON DELETE CASCADE
);

ALTER TABLE training_plans MODIFY COLUMN weekly_schedule TEXT NULL;
