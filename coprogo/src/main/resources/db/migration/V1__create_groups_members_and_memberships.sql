CREATE DOMAIN member_email_address AS TEXT
    CHECK (VALUE = lower(btrim(VALUE)))
    CHECK (VALUE <> '');

CREATE TABLE members (
    email member_email_address PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE groups (
    id UUID PRIMARY KEY,
    created_by member_email_address NOT NULL REFERENCES members (email),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE group_memberships (
    id UUID PRIMARY KEY,
    "group" UUID NOT NULL REFERENCES groups (id),
    member_email member_email_address NOT NULL REFERENCES members (email),
    joined_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT group_memberships_group_member_unique UNIQUE ("group", member_email)
);

CREATE INDEX group_memberships_group_idx ON group_memberships ("group");
CREATE INDEX group_memberships_member_email_idx ON group_memberships (member_email);
