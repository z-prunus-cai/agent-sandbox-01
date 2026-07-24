-- ENVIRONMENT-ONLY demo data for: dev (never loaded in test/stg/prod).
-- Depends on the customer_status reference data (R__5xx, runs first).
-- Repeatable + idempotent: keyed on external_ref so re-runs never duplicate.
MERGE INTO app.customer AS tgt
USING (VALUES
    (N'DEMO-0001', N'Acme Corporation (dev)', N'ACTIVE'),
    (N'DEMO-0002', N'Globex LLC (dev)',       N'ACTIVE'),
    (N'DEMO-0003', N'Initech (dev)',          N'INACTIVE'),
    (N'DEMO-0004', N'Umbrella Corp (dev)',    N'SUSPENDED')
) AS src(external_ref, display_name, status)
ON (tgt.external_ref = src.external_ref)
WHEN MATCHED AND (tgt.display_name <> src.display_name OR tgt.status <> src.status)
    THEN UPDATE SET tgt.display_name = src.display_name, tgt.status = src.status
WHEN NOT MATCHED BY TARGET
    THEN INSERT (external_ref, display_name, status)
         VALUES (src.external_ref, src.display_name, src.status);
GO
