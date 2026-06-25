CREATE INDEX IF NOT EXISTS idx_request_logs_run_id ON request_logs(run_id);
CREATE INDEX IF NOT EXISTS idx_request_logs_endpoint_id ON request_logs(endpoint_id);
CREATE INDEX IF NOT EXISTS idx_request_logs_run_endpoint ON request_logs(run_id, endpoint_id);
