ALTER TABLE expense_participations
    ADD COLUMN refusal_reason TEXT,
    ADD CONSTRAINT expense_participations_refusal_reason_check CHECK (
        refusal_reason IS NULL
        OR (status = 'REFUSED' AND btrim(refusal_reason) <> '')
    );
