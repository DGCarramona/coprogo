CREATE TYPE expense_status AS ENUM (
    'PROPOSED',
    'ACCEPTED',
    'INVALIDATED'
);

CREATE TYPE expense_participation_status AS ENUM (
    'PENDING',
    'APPROVED',
    'REFUSED'
);

CREATE TABLE expenses (
    id UUID PRIMARY KEY,
    "group" UUID NOT NULL REFERENCES groups (id),
    title TEXT NOT NULL,
    created_by member_email_address NOT NULL REFERENCES members (email),
    total_amount positive_money_amount_cents NOT NULL,
    status expense_status NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE expense_participations (
    id UUID PRIMARY KEY,
    expense UUID NOT NULL REFERENCES expenses (id) ON DELETE CASCADE,
    member member_email_address NOT NULL REFERENCES members (email),
    amount positive_money_amount_cents NOT NULL,
    status expense_participation_status NOT NULL,
    decided_at TIMESTAMPTZ,
    CONSTRAINT expense_participations_expense_member_unique UNIQUE (expense, member),
    CONSTRAINT expense_participations_decided_at_check CHECK (
        (status = 'PENDING' AND decided_at IS NULL)
        OR (status <> 'PENDING' AND decided_at IS NOT NULL)
    )
);

CREATE INDEX expenses_group_idx ON expenses ("group", created_at, id);
CREATE INDEX expense_participations_expense_idx ON expense_participations (expense);
