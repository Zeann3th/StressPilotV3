-- Migrate runs.id and request_logs.run_id from BIGINT to VARCHAR(20) for Snowflake IDs

-- Step 1: drop FK from request_logs → runs
ALTER TABLE request_logs DROP CONSTRAINT IF EXISTS fk_request_logs_on_run;

-- Step 2: change request_logs.run_id to VARCHAR(20)
ALTER TABLE request_logs ALTER COLUMN run_id TYPE VARCHAR(20) USING run_id::VARCHAR;

-- Step 3: change runs.id to VARCHAR(20)
ALTER TABLE runs DROP CONSTRAINT IF EXISTS pk_runs;
ALTER TABLE runs ALTER COLUMN id TYPE VARCHAR(20) USING id::VARCHAR;
ALTER TABLE runs ADD CONSTRAINT pk_runs PRIMARY KEY (id);

-- Step 4: re-add FK
ALTER TABLE request_logs
    ADD CONSTRAINT fk_request_logs_on_run FOREIGN KEY (run_id) REFERENCES runs (id);