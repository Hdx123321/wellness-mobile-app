CREATE TABLE coach_conversations (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  client_id BIGINT NOT NULL,
  coach_id BIGINT NOT NULL,
  created_at TIMESTAMP(6) NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL,
  CONSTRAINT uk_coach_conversations_client UNIQUE (client_id),
  CONSTRAINT fk_coach_conversations_client FOREIGN KEY (client_id) REFERENCES users (id),
  CONSTRAINT fk_coach_conversations_coach FOREIGN KEY (coach_id) REFERENCES users (id)
);

CREATE INDEX idx_coach_conversations_coach_updated
  ON coach_conversations (coach_id, updated_at DESC);

CREATE TABLE coach_messages (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  conversation_id BIGINT NOT NULL,
  sender_id BIGINT NOT NULL,
  content VARCHAR(2000) NOT NULL,
  created_at TIMESTAMP(6) NOT NULL,
  CONSTRAINT fk_coach_messages_conversation FOREIGN KEY (conversation_id)
    REFERENCES coach_conversations (id) ON DELETE CASCADE,
  CONSTRAINT fk_coach_messages_sender FOREIGN KEY (sender_id) REFERENCES users (id)
);

CREATE INDEX idx_coach_messages_conversation_id
  ON coach_messages (conversation_id, id);
