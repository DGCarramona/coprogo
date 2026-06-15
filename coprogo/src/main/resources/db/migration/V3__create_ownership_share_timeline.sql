CREATE DOMAIN ownership_basis_points AS INTEGER
    CHECK (VALUE BETWEEN 1 AND 10000);

CREATE TABLE ownership_share_changes (
    id UUID PRIMARY KEY,
    "group" UUID NOT NULL REFERENCES groups (id),
    effective_date DATE NOT NULL,
    recorded_by member_email_address NOT NULL REFERENCES members (email),
    recorded_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ownership_share_changes_unique_group_effective_date UNIQUE ("group", effective_date)
);

CREATE TABLE ownership_share_allocations (
    id UUID PRIMARY KEY,
    change_id UUID NOT NULL REFERENCES ownership_share_changes (id),
    member_email member_email_address NOT NULL REFERENCES members (email),
    basis_points ownership_basis_points NOT NULL,
    CONSTRAINT ownership_share_allocations_unique_change_member UNIQUE (change_id, member_email)
);

CREATE INDEX ownership_share_changes_group_idx ON ownership_share_changes ("group");
CREATE INDEX ownership_share_allocations_change_idx ON ownership_share_allocations (change_id);
