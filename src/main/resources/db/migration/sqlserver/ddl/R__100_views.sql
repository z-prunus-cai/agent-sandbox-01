-- COMMON structural repeatable: views (re-applied when the checksum changes).
-- Repeatable migrations run after all versioned ones; the R__1xx prefix keeps
-- structural objects ordered before the reference-data reinserts (R__5xx/R__8xx).
-- On SQL Server CREATE VIEW must be alone in its batch, so use CREATE OR ALTER.

CREATE OR ALTER VIEW app.vw_active_customer AS
SELECT
    c.id,
    c.external_ref,
    c.display_name,
    c.created_at
FROM app.customer c
WHERE c.status = N'ACTIVE';
GO
