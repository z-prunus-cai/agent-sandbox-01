-- ENVIRONMENT-SPECIFIC master data for: test
-- Loaded only when app.env=test. Repeatable + idempotent MERGE.
MERGE INTO app.app_config AS tgt
USING (VALUES
    (N'env.name',                 N'test'),
    (N'feature.beta-banner',      N'false'),
    (N'notification.webhook-url', N'https://hooks.test.internal/db-events')
) AS src(config_key, config_value)
ON (tgt.config_key = src.config_key)
WHEN MATCHED AND tgt.config_value <> src.config_value
    THEN UPDATE SET tgt.config_value = src.config_value
WHEN NOT MATCHED BY TARGET
    THEN INSERT (config_key, config_value) VALUES (src.config_key, src.config_value);
GO
