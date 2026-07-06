-- Add subject column for coach-labeled conversations
ALTER TABLE coach_conversations ADD COLUMN subject VARCHAR(120) NULL;

-- Drop FK that depends on the unique index
ALTER TABLE coach_conversations DROP FOREIGN KEY fk_coach_conversations_client;

-- Now we can drop the unique index
ALTER TABLE coach_conversations DROP INDEX uk_coach_conversations_client;

-- Composite unique: one conversation per client-coach pair
ALTER TABLE coach_conversations ADD CONSTRAINT uk_client_coach UNIQUE (client_id, coach_id);

-- Restore FK (prefix of composite index still supports FK lookups on client_id)
ALTER TABLE coach_conversations ADD CONSTRAINT fk_coach_conversations_client
  FOREIGN KEY (client_id) REFERENCES users (id);
