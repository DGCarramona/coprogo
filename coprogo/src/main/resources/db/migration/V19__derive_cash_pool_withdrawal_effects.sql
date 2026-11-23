LOCK TABLE ledger_events, ledger_cash_pool_income_events, ledger_cash_pool_withdrawal_events,
    ledger_member_balance_transfers, ledger_member_cash_pool_share_deltas IN ACCESS EXCLUSIVE MODE;

DO $$
BEGIN
    IF EXISTS (
        WITH movements AS (
            SELECT event."group", (event.occurred_at AT TIME ZONE 'UTC')::date AS day,
                   income.amount_in_cents::numeric AS amount
            FROM ledger_events event
            JOIN ledger_cash_pool_income_events income ON income.event = event.id
            UNION ALL
            SELECT event."group", (event.occurred_at AT TIME ZONE 'UTC')::date AS day,
                   -withdrawal.withdrawn_amount_in_cents::numeric AS amount
            FROM ledger_events event
            JOIN ledger_cash_pool_withdrawal_events withdrawal ON withdrawal.event = event.id
        ), daily AS (
            SELECT "group", day, sum(amount) AS amount
            FROM movements
            GROUP BY "group", day
        ), balances AS (
            SELECT sum(amount) OVER (PARTITION BY "group" ORDER BY day) AS balance
            FROM daily
        )
        SELECT 1 FROM balances WHERE balance < 0
    ) THEN
        RAISE EXCEPTION 'Cannot migrate cash-pool withdrawals: a group has a negative daily balance';
    END IF;
END $$;

DELETE FROM ledger_member_balance_transfers transfer
USING ledger_cash_pool_withdrawal_events withdrawal
WHERE transfer.event = withdrawal.event;

DELETE FROM ledger_member_cash_pool_share_deltas delta
USING ledger_cash_pool_withdrawal_events withdrawal
WHERE delta.event = withdrawal.event;

ALTER TABLE ledger_cash_pool_withdrawal_events DROP COLUMN own_revenue_share_consumed_in_cents;