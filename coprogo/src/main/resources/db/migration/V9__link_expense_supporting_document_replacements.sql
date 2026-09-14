ALTER TABLE expense_supporting_documents
    ADD COLUMN replaces_source_upload_intent UUID,
    ADD CONSTRAINT expense_supporting_documents_source_group_expense_unique
        UNIQUE (source_upload_intent, "group", expense),
    ADD CONSTRAINT expense_supporting_documents_replacement_not_self_check
        CHECK (replaces_source_upload_intent IS NULL OR replaces_source_upload_intent <> source_upload_intent),
    ADD CONSTRAINT expense_supporting_documents_replaced_once_unique
        UNIQUE (replaces_source_upload_intent),
    ADD CONSTRAINT expense_supporting_documents_replacement_same_expense_fk
        FOREIGN KEY (replaces_source_upload_intent, "group", expense)
        REFERENCES expense_supporting_documents (source_upload_intent, "group", expense);
