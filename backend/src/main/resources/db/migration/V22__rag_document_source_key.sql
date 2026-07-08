ALTER TABLE rag_documents
  ADD COLUMN source_key VARCHAR(120) NULL;

CREATE UNIQUE INDEX ux_rag_documents_user_type_source
  ON rag_documents (user_id, doc_type, source_key);
