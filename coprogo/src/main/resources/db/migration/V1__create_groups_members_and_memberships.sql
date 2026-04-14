CREATE TABLE members (
    email TEXT PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT members_email_normalized CHECK (email = lower(btrim(email))),
    CONSTRAINT members_email_not_blank CHECK (email <> '')
);

CREATE TABLE groups (
    id UUID PRIMARY KEY,
    created_by TEXT NOT NULL REFERENCES members (email),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE group_memberships (
    id UUID PRIMARY KEY,
    "group" UUID NOT NULL REFERENCES groups (id),
    member_email TEXT NOT NULL REFERENCES members (email),
    joined_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT group_memberships_group_member_unique UNIQUE ("group", member_email)
);

CREATE INDEX group_memberships_group_idx ON group_memberships ("group");
CREATE INDEX group_memberships_member_email_idx ON group_memberships (member_email);
