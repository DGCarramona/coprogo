ALTER TABLE supporting_document_attachments
    ADD COLUMN deleted_by member_email_address,
    ADD COLUMN deleted_at TIMESTAMPTZ,
    ADD CONSTRAINT supporting_document_attachments_deletion_audit_check CHECK (
        (deleted_by IS NULL AND deleted_at IS NULL)
        OR (deleted_by IS NOT NULL AND deleted_at IS NOT NULL)
    ),
    ADD CONSTRAINT supporting_document_attachments_deleted_by_group_fk
        FOREIGN KEY ("group", deleted_by)
        REFERENCES group_memberships ("group", member_email);
