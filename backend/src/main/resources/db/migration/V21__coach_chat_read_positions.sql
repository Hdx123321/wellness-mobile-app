ALTER TABLE coach_conversations
  ADD COLUMN client_last_read_message_id BIGINT NOT NULL DEFAULT 0;

ALTER TABLE coach_conversations
  ADD COLUMN coach_last_read_message_id BIGINT NOT NULL DEFAULT 0;
