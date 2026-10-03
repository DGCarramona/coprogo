CREATE TYPE reimbursement_review_decision AS ENUM (
    'ACCEPTED',
    'REJECTED'
);

ALTER TABLE reimbursements
    DROP CONSTRAINT reimbursements_status_acceptance_check,
    DROP COLUMN status,
    DROP COLUMN accepted_at,
    ADD CONSTRAINT reimbursements_id_group_receiver_unique
        UNIQUE (id, "group", received_by);

DROP TYPE reimbursement_status;

CREATE TABLE reimbursement_review_decisions (
    reimbursement UUID PRIMARY KEY,
    "group" UUID NOT NULL,
    reviewed_by member_email_address NOT NULL,
    decision reimbursement_review_decision NOT NULL,
    decided_at TIMESTAMPTZ NOT NULL,
    rejection_reason TEXT,
    CONSTRAINT reimbursement_review_decisions_reason_check CHECK (
        (decision = 'ACCEPTED' AND rejection_reason IS NULL)
        OR (
            decision = 'REJECTED'
            AND (rejection_reason IS NULL OR BTRIM(rejection_reason) <> '')
        )
    ),
    CONSTRAINT reimb_review_decisions_receiver_fk
        FOREIGN KEY (reimbursement, "group", reviewed_by)
        REFERENCES reimbursements (id, "group", received_by)
        ON UPDATE RESTRICT
        ON DELETE RESTRICT
);

CREATE INDEX reimbursement_review_decisions_group_decided_at_idx
    ON reimbursement_review_decisions ("group", decided_at, reimbursement);

CREATE FUNCTION validate_reimbursement_review_decision()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    reviewable BOOLEAN;
    stored_declared_at TIMESTAMPTZ;
BEGIN
    SELECT declared_by = paid_by, declared_at
    INTO reviewable, stored_declared_at
    FROM reimbursements
    WHERE id = NEW.reimbursement
        AND "group" = NEW."group"
    FOR SHARE;

    IF NOT FOUND THEN
        RETURN NEW;
    END IF;

    IF NOT reviewable THEN
        RAISE check_violation
            USING MESSAGE = 'only a reimbursement declared by its payer can be reviewed';
    END IF;

    IF NEW.decided_at < stored_declared_at THEN
        RAISE check_violation
            USING MESSAGE = 'reimbursement review decision must not precede its declaration';
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER reimbursement_review_decisions_validate_trigger
    BEFORE INSERT ON reimbursement_review_decisions
    FOR EACH ROW
    EXECUTE FUNCTION validate_reimbursement_review_decision();

CREATE FUNCTION forbid_reimbursement_review_decision_change()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'reimbursement review decisions are append-only'
        USING ERRCODE = '55000';
END;
$$;

CREATE TRIGGER reimbursement_review_decisions_immutable_trigger
    BEFORE UPDATE OR DELETE ON reimbursement_review_decisions
    FOR EACH ROW
    EXECUTE FUNCTION forbid_reimbursement_review_decision_change();
