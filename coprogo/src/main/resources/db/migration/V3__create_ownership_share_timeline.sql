CREATE TABLE ownership_share_changes (
    id UUID PRIMARY KEY,
    "group" UUID NOT NULL REFERENCES groups (id),
    effective_date DATE NOT NULL,
    recorded_by TEXT NOT NULL REFERENCES members (email),
    recorded_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ownership_share_changes_unique_group_effective_date UNIQUE ("group", effective_date)
);

CREATE TABLE ownership_share_allocations (
    id UUID PRIMARY KEY,
    change_id UUID NOT NULL REFERENCES ownership_share_changes (id),
    member_email TEXT NOT NULL REFERENCES members (email),
    basis_points INTEGER NOT NULL,
    CONSTRAINT ownership_share_allocations_unique_change_member UNIQUE (change_id, member_email),
    CONSTRAINT ownership_share_allocations_member_email_normalized CHECK (member_email = lower(btrim(member_email))),
    CONSTRAINT ownership_share_allocations_member_email_not_blank CHECK (member_email <> ''),
    CONSTRAINT ownership_share_allocations_basis_points_range CHECK (basis_points BETWEEN 1 AND 10000)
);

CREATE INDEX ownership_share_changes_group_idx ON ownership_share_changes ("group");
CREATE INDEX ownership_share_allocations_change_idx ON ownership_share_allocations (change_id);
