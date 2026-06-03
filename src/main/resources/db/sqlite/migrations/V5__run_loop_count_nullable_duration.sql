CREATE TABLE runs_new
(
    id               VARCHAR(20) PRIMARY KEY NOT NULL,
    flow_id          INTEGER                 NOT NULL,
    status           VARCHAR(10)             NOT NULL,
    threads          INTEGER                 NOT NULL,
    duration         INTEGER,
    loop_count       INTEGER,
    ramp_up_duration INTEGER                 NOT NULL,
    started_at       TIMESTAMP               NOT NULL,
    completed_at     TIMESTAMP,
    CONSTRAINT FK_RUNS_ON_FLOW FOREIGN KEY (flow_id) REFERENCES flows (id) ON DELETE CASCADE
);

INSERT INTO runs_new (id, flow_id, status, threads, duration, ramp_up_duration, started_at, completed_at)
SELECT id, flow_id, status, threads, duration, ramp_up_duration, started_at, completed_at
FROM runs;

DROP TABLE runs;
ALTER TABLE runs_new RENAME TO runs;
