CREATE TABLE runs_snapshot
(
    id               VARCHAR(20)                 NOT NULL,
    flow_id          BIGINT                      NOT NULL,
    status           VARCHAR(10)                 NOT NULL,
    threads          INTEGER                     NOT NULL,
    duration         INTEGER                     NOT NULL,
    ramp_up_duration INTEGER                     NOT NULL,
    started_at       TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    completed_at     TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    metrics          TEXT                        NOT NULL,
    created_at       TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT pk_runs_snapshot PRIMARY KEY (id)
);
