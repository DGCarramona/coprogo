CREATE TYPE reimbursement_status AS ENUM (
    'PENDING_REVIEW',
    'ACCEPTED'
);

CREATE TABLE reimbursements (
    id UUID PRIMARY KEY,
    "group" UUID NOT NULL,
    paid_by member_email_address NOT NULL,
    received_by member_email_address NOT NULL,
    amount positive_money_amount_cents NOT NULL,
    reimbursed_at TIMESTAMPTZ NOT NULL,
    declared_by member_email_address NOT NULL,
    declared_at TIMESTAMPTZ NOT NULL,
    status reimbursement_status NOT NULL,
    accepted_at TIMESTAMPTZ,
    CONSTRAINT reimbursements_paid_by_group_fk
        FOREIGN KEY ("group", paid_by)
        REFERENCES group_memberships ("group", member_email),
    CONSTRAINT reimbursements_received_by_group_fk
        FOREIGN KEY ("group", received_by)
        REFERENCES group_memberships ("group", member_email),
    CONSTRAINT reimbursements_declared_by_group_fk
        FOREIGN KEY ("group", declared_by)
        REFERENCES group_memberships ("group", member_email),
    CONSTRAINT reimbursements_payer_receiver_different_check
        CHECK (paid_by <> received_by),
    CONSTRAINT reimbursements_declarer_check
        CHECK (declared_by = paid_by OR declared_by = received_by),
    CONSTRAINT reimbursements_occurred_before_declaration_check
        CHECK (reimbursed_at <= declared_at),
    CONSTRAINT reimbursements_status_acceptance_check CHECK (
        (status = 'PENDING_REVIEW' AND declared_by = paid_by AND accepted_at IS NULL)
        OR (status = 'ACCEPTED' AND accepted_at IS NOT NULL AND accepted_at >= declared_at)
    ),
    CONSTRAINT reimbursements_id_group_declarer_unique UNIQUE (id, "group", declared_by)
);

CREATE INDEX reimbursements_group_reimbursed_at_idx
    ON reimbursements ("group", reimbursed_at, id);

ALTER TABLE document_upload_intents
    ADD CONSTRAINT document_upload_intents_id_group_uploader_status_unique
        UNIQUE (id, "group", uploader, status);

CREATE TABLE reimbursement_supporting_documents (
    source_upload_intent UUID PRIMARY KEY,
    "group" UUID NOT NULL,
    source_status TEXT NOT NULL,
    type supporting_document_attachment_type NOT NULL,
    reimbursement UUID NOT NULL,
    uploader member_email_address NOT NULL,
    CONSTRAINT reimbursement_supporting_documents_source_status_check
        CHECK (source_status = 'CONSUMED'),
    CONSTRAINT reimbursement_supporting_documents_type_check
        CHECK (type = 'REIMBURSEMENT'),
    CONSTRAINT reimbursement_supporting_documents_attachment_fk
        FOREIGN KEY (source_upload_intent, "group", type)
        REFERENCES supporting_document_attachments (source_upload_intent, "group", type),
    CONSTRAINT reimb_docs_intent_uploader_status_fk
        FOREIGN KEY (source_upload_intent, "group", uploader, source_status)
        REFERENCES document_upload_intents (id, "group", uploader, status),
    CONSTRAINT reimb_docs_reimbursement_declarer_fk
        FOREIGN KEY (reimbursement, "group", uploader)
        REFERENCES reimbursements (id, "group", declared_by)
);

CREATE INDEX reimbursement_supporting_documents_group_reimbursement_idx
    ON reimbursement_supporting_documents ("group", reimbursement, source_upload_intent);
