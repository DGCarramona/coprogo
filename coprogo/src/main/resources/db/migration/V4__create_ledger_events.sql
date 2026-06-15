CREATE TYPE ledger_event_type AS ENUM (
    'ACCEPTED_EXPENSE',
    'CASH_POOL_INCOME',
    'CASH_POOL_WITHDRAWAL'
);

CREATE DOMAIN money_amount_cents AS BIGINT
    CHECK (VALUE >= 0);

CREATE DOMAIN positive_money_amount_cents AS BIGINT
    CHECK (VALUE > 0);

CREATE DOMAIN net_money_delta_cents AS BIGINT
    CHECK (VALUE <> 0);

CREATE TABLE ledger_events (
    id UUID PRIMARY KEY,
    "group" UUID NOT NULL REFERENCES groups (id),
    type ledger_event_type NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE ledger_accepted_expense_events (
    event UUID PRIMARY KEY REFERENCES ledger_events (id),
    expense UUID NOT NULL,
    paid_by member_email_address NOT NULL REFERENCES members (email)
);

CREATE TABLE ledger_cash_pool_income_events (
    event UUID PRIMARY KEY REFERENCES ledger_events (id),
    amount_in_cents positive_money_amount_cents NOT NULL
);

CREATE TABLE ledger_cash_pool_withdrawal_events (
    event UUID PRIMARY KEY REFERENCES ledger_events (id),
    withdrawn_by member_email_address NOT NULL REFERENCES members (email),
    withdrawn_amount_in_cents positive_money_amount_cents NOT NULL,
    own_revenue_share_consumed_in_cents money_amount_cents NOT NULL
);

CREATE TABLE ledger_member_balance_transfers (
    id UUID PRIMARY KEY,
    event UUID NOT NULL REFERENCES ledger_events (id),
    from_member member_email_address NOT NULL REFERENCES members (email),
    to_member member_email_address NOT NULL REFERENCES members (email),
    amount_in_cents positive_money_amount_cents NOT NULL,
    CONSTRAINT ledger_member_balance_transfers_members_different CHECK (from_member <> to_member)
);

CREATE TABLE ledger_member_cash_pool_share_deltas (
    id UUID PRIMARY KEY,
    event UUID NOT NULL REFERENCES ledger_events (id),
    member_email member_email_address NOT NULL REFERENCES members (email),
    amount_in_cents net_money_delta_cents NOT NULL
);

CREATE INDEX ledger_events_group_occurred_at_idx ON ledger_events ("group", occurred_at, id);
CREATE INDEX ledger_member_balance_transfers_event_idx ON ledger_member_balance_transfers (event);
CREATE INDEX ledger_member_cash_pool_share_deltas_event_idx ON ledger_member_cash_pool_share_deltas (event);
