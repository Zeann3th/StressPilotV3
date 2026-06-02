ALTER TABLE request_logs
    ADD COLUMN IF NOT EXISTS correlation_id VARCHAR(20);
