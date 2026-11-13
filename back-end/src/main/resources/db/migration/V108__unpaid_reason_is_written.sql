-- Closing an order as unpaid has no ready-made reasons any more (user, 2026-11-01): staff write it. "Will pay later" as a choice
-- made waiters think guests may pay later. Only the system's own "no action taken" stays a kind.
UPDATE orders
SET unpaid_note = COALESCE(unpaid_note, CASE unpaid_reason WHEN 'WALKED_OUT' THEN 'Walked out' WHEN 'PAY_LATER' THEN 'Will pay later' END),
    unpaid_reason = 'OTHER'
WHERE unpaid_reason IN ('WALKED_OUT', 'PAY_LATER');
