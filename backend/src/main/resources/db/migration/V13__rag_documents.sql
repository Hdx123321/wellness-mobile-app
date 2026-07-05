CREATE TABLE rag_documents (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  session_id BIGINT NULL,
  doc_type VARCHAR(30) NOT NULL,
  title VARCHAR(200) NOT NULL,
  content TEXT NOT NULL,
  embedding JSON NOT NULL,
  metadata JSON NOT NULL,
  created_at TIMESTAMP(6) NOT NULL,
  CONSTRAINT fk_rag_documents_user FOREIGN KEY (user_id) REFERENCES users (id),
  CONSTRAINT fk_rag_documents_session FOREIGN KEY (session_id) REFERENCES chat_sessions (id) ON DELETE SET NULL,
  CONSTRAINT ck_rag_documents_type CHECK (doc_type IN ('WEEKLY_REPORT', 'MONTHLY_REPORT', 'CONVERSATION_SUMMARY'))
);

CREATE INDEX idx_rag_documents_user_type_created
  ON rag_documents (user_id, doc_type, created_at DESC);
