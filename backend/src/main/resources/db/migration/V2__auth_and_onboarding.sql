ALTER TABLE users
  ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'CLIENT';

ALTER TABLE users
  ADD COLUMN onboarding_completed BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE user_profiles (
  user_id BIGINT PRIMARY KEY,
  date_of_birth DATE NOT NULL,
  height_cm DECIMAL(5, 2) NOT NULL,
  current_weight_kg DECIMAL(5, 2) NOT NULL,
  sex VARCHAR(30) NOT NULL,
  ethnicity VARCHAR(50),
  target_weight_kg DECIMAL(5, 2),
  goal_started_at DATE,
  goal_duration_weeks INT,
  daily_routine VARCHAR(40) NOT NULL,
  activity_level VARCHAR(30) NOT NULL,
  created_at TIMESTAMP(6) NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL,
  CONSTRAINT fk_user_profiles_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
  CONSTRAINT ck_user_profiles_height CHECK (height_cm BETWEEN 80 AND 250),
  CONSTRAINT ck_user_profiles_weight CHECK (current_weight_kg BETWEEN 25 AND 350),
  CONSTRAINT ck_user_profiles_target_weight CHECK (target_weight_kg IS NULL OR target_weight_kg BETWEEN 25 AND 350),
  CONSTRAINT ck_user_profiles_goal_duration CHECK (goal_duration_weeks IS NULL OR goal_duration_weeks BETWEEN 1 AND 260)
);

CREATE TABLE user_exercise_preferences (
  user_id BIGINT NOT NULL,
  preference VARCHAR(40) NOT NULL,
  PRIMARY KEY (user_id, preference),
  CONSTRAINT fk_user_exercise_preferences_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE user_core_needs (
  user_id BIGINT NOT NULL,
  core_need VARCHAR(40) NOT NULL,
  PRIMARY KEY (user_id, core_need),
  CONSTRAINT fk_user_core_needs_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
