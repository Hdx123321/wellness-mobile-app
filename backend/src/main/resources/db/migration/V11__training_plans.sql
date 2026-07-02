CREATE TABLE training_plans (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  coach_id BIGINT NOT NULL,
  title VARCHAR(120) NOT NULL,
  goal VARCHAR(120) NOT NULL,
  difficulty VARCHAR(30) NOT NULL,
  duration_weeks INT NOT NULL,
  summary VARCHAR(1000) NOT NULL,
  weekly_schedule TEXT NOT NULL,
  equipment VARCHAR(500),
  safety_notes VARCHAR(1000),
  published BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP(6) NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL,
  CONSTRAINT fk_training_plans_coach FOREIGN KEY (coach_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE training_plan_check_ins (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  plan_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  check_in_date DATE NOT NULL,
  created_at TIMESTAMP(6) NOT NULL,
  CONSTRAINT fk_plan_check_ins_plan FOREIGN KEY (plan_id) REFERENCES training_plans (id) ON DELETE CASCADE,
  CONSTRAINT fk_plan_check_ins_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
  CONSTRAINT uk_plan_daily_check_in UNIQUE (plan_id, user_id, check_in_date)
);
