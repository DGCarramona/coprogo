ALTER TYPE ledger_event_type ADD VALUE 'ACCEPTED_REIMBURSEMENT';

CREATE TABLE ledger_accepted_reimbursement_events (
    event UUID PRIMARY KEY
        REFERENCES ledger_events (id)
        ON UPDATE RESTRICT
        ON DELETE RESTRICT,
    reimbursement UUID NOT NULL UNIQUE
        REFERENCES reimbursements (id)
        ON UPDATE RESTRICT
        ON DELETE RESTRICT
);
