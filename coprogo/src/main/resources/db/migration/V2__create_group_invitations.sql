CREATE TABLE group_invitations (
    id UUID PRIMARY KEY,
    "group" UUID NOT NULL REFERENCES groups (id),
    invited_email TEXT NOT NULL,
    invited_by TEXT NOT NULL REFERENCES members (email),
    invited_at TIMESTAMPTZ NOT NULL,
    accepted_by TEXT NULL REFERENCES members (email),
    accepted_at TIMESTAMPTZ NULL,
    CONSTRAINT group_invitations_invited_email_normalized CHECK (invited_email = lower(btrim(invited_email))),
    CONSTRAINT group_invitations_invited_email_not_blank CHECK (invited_email <> ''),
    CONSTRAINT group_invitations_unique_group_invited_email UNIQUE ("group", invited_email),
    CONSTRAINT group_invitations_acceptance_consistency CHECK (
        (accepted_by IS NULL AND accepted_at IS NULL) OR
        (accepted_by IS NOT NULL AND accepted_at IS NOT NULL)
    )
);

CREATE INDEX group_invitations_group_pending_idx
    ON group_invitations ("group")
    WHERE accepted_at IS NULL;

CREATE INDEX group_invitations_invited_email_pending_idx
    ON group_invitations (invited_email)
    WHERE accepted_at IS NULL;
