CREATE TABLE document_upload_intents (
    id UUID PRIMARY KEY,
    "group" UUID NOT NULL,
    uploader member_email_address NOT NULL,
    storage_key TEXT NOT NULL UNIQUE,
    file_name TEXT NOT NULL,
    media_type TEXT NOT NULL,
    expected_size BIGINT NOT NULL,
    expected_sha256 TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    status TEXT NOT NULL,
    ready_at TIMESTAMPTZ,
    consumed_at TIMESTAMPTZ,
    CONSTRAINT document_upload_intents_group_uploader_fk
        FOREIGN KEY ("group", uploader)
        REFERENCES group_memberships ("group", member_email),
    CONSTRAINT document_upload_intents_storage_key_check CHECK (btrim(storage_key) <> ''),
    CONSTRAINT document_upload_intents_file_name_check CHECK (
        btrim(file_name) <> ''
        AND length(file_name) <= 255
        AND file_name !~ '[[:cntrl:]/\\]'
    ),
    CONSTRAINT document_upload_intents_media_type_check CHECK (
        media_type ~ '^[a-z0-9][a-z0-9!#$&^_.+-]*/[a-z0-9][a-z0-9!#$&^_.+-]*$'
    ),
    CONSTRAINT document_upload_intents_expected_size_check CHECK (expected_size > 0),
    CONSTRAINT document_upload_intents_expected_sha256_check CHECK (
        octet_length(decode(expected_sha256, 'base64')) = 32
        AND encode(decode(expected_sha256, 'base64'), 'base64') = expected_sha256
    ),
    CONSTRAINT document_upload_intents_lifetime_check CHECK (expires_at > created_at),
    CONSTRAINT document_upload_intents_status_check CHECK (status IN ('PENDING', 'READY', 'CONSUMED')),
    CONSTRAINT document_upload_intents_state_check CHECK (
        (status = 'PENDING' AND ready_at IS NULL AND consumed_at IS NULL)
        OR (status = 'READY' AND ready_at IS NOT NULL AND consumed_at IS NULL)
        OR (status = 'CONSUMED' AND ready_at IS NOT NULL AND consumed_at IS NOT NULL)
    ),
    CONSTRAINT document_upload_intents_ready_at_check CHECK (
        ready_at IS NULL OR (ready_at >= created_at AND ready_at < expires_at)
    ),
    CONSTRAINT document_upload_intents_consumed_at_check CHECK (
        consumed_at IS NULL OR (ready_at IS NOT NULL AND consumed_at >= ready_at AND consumed_at < expires_at)
    )
);

CREATE INDEX document_upload_intents_group_uploader_idx
    ON document_upload_intents ("group", uploader);

CREATE INDEX document_upload_intents_status_expires_at_idx
    ON document_upload_intents (status, expires_at);
