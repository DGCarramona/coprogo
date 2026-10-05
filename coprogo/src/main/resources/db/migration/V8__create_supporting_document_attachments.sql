CREATE TYPE supporting_document_attachment_type AS ENUM (
    'EXPENSE'
);

ALTER TABLE document_upload_intents
    ADD CONSTRAINT document_upload_intents_id_group_status_unique UNIQUE (id, "group", status);

ALTER TABLE expenses
    ADD CONSTRAINT expenses_id_group_unique UNIQUE (id, "group");

CREATE TABLE supporting_document_attachments (
    source_upload_intent UUID PRIMARY KEY,
    "group" UUID NOT NULL,
    source_status TEXT NOT NULL,
    type supporting_document_attachment_type NOT NULL,
    CONSTRAINT supporting_document_attachments_source_status_check
        CHECK (source_status = 'CONSUMED'),
    CONSTRAINT supporting_document_attachments_source_group_type_unique
        UNIQUE (source_upload_intent, "group", type),
    CONSTRAINT supporting_document_attachments_source_group_status_fk
        FOREIGN KEY (source_upload_intent, "group", source_status)
        REFERENCES document_upload_intents (id, "group", status)
);

CREATE TABLE expense_supporting_documents (
    source_upload_intent UUID PRIMARY KEY,
    "group" UUID NOT NULL,
    type supporting_document_attachment_type NOT NULL,
    expense UUID NOT NULL,
    CONSTRAINT expense_supporting_documents_type_check CHECK (type = 'EXPENSE'),
    CONSTRAINT expense_supporting_documents_attachment_fk
        FOREIGN KEY (source_upload_intent, "group", type)
        REFERENCES supporting_document_attachments (source_upload_intent, "group", type),
    CONSTRAINT expense_supporting_documents_expense_group_fk
        FOREIGN KEY (expense, "group")
        REFERENCES expenses (id, "group")
);

CREATE INDEX expense_supporting_documents_group_expense_idx
    ON expense_supporting_documents ("group", expense, source_upload_intent);
