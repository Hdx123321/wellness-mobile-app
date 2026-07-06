CREATE TABLE plan_subscriptions (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  plan_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  subscribed_at TIMESTAMP(6) NOT NULL,
  CONSTRAINT fk_plan_sub_plan FOREIGN KEY (plan_id) REFERENCES training_plans (id) ON DELETE CASCADE,
  CONSTRAINT fk_plan_sub_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
  CONSTRAINT uk_plan_sub UNIQUE (plan_id, user_id)
);
