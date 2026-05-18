# Design Spec - Run Snapshotting Scheduled Job

Create a scheduled job that periodically snapshots completed runs into a flat, independent table to allow for long-term storage and visualization even after original logs are purged.

## 1. Overview
The system will run a background task (Quartz) every 10 minutes (or as configured). It will identify runs that are `COMPLETED` but haven't been snapshotted yet. For each eligible run, it will aggregate its request logs into 20 time-based bins per endpoint and save the resulting data into a flat `runs_snapshot` table.

## 2. Requirements
- **Frequency**: Every 10 minutes (default) or via `app.tasks.run-snapshot.cron`.
- **Eligibility**: Status = `COMPLETED` and `run_id` NOT in `runs_snapshot`.
- **Data Integrity**: No foreign keys to `runs` or `request_logs` to allow for deletion of source data.
- **Aggregation**: 20 bins per endpoint, averaging response times within each bin.

## 3. Data Model

### `runs_snapshot` Table
| Column | Type | Description |
| --- | --- | --- |
| `id` | VARCHAR(20) | Primary Key (matches original Run ID) |
| `flow_id` | BIGINT | Original Flow ID |
| `status` | VARCHAR(10) | Status (COMPLETED) |
| `threads` | INTEGER | Thread count |
| `duration` | INTEGER | Planned duration |
| `ramp_up_duration` | INTEGER | Ramp up duration |
| `started_at` | TIMESTAMP | Start time |
| `completed_at` | TIMESTAMP | Completion time |
| `metrics` | TEXT | JSON string containing binned data |
| `created_at` | TIMESTAMP | When the snapshot was created |

### Metrics JSON Structure
```json
{
  "endpoint_id": [
    {
      "bin_index": 0,
      "avg_response_time": 150.5,
      "request_count": 100,
      "start_time": "2026-05-18T10:00:00"
    },
    ...
  ]
}
```

## 4. Aggregation Logic
For each run:
1. Determine time range: `total_ms = (completed_at - started_at)` in milliseconds.
2. Calculate `bin_width = total_ms / 20`.
3. Fetch all `request_logs` for the run.
4. For each log:
   - `offset = log.created_at - started_at`
   - `bin_index = min(19, floor(offset / bin_width))`
5. Group by `endpoint_id` and `bin_index`.
6. Calculate `avg(response_time)` and `count(*)` per group.
7. Fill missing bins (if any) with zero/null values to ensure exactly 20 bins per endpoint.

## 5. Implementation Plan
- **Migration**: Create `V11__add_runs_snapshot.sql`.
- **Entity**: Create `RunSnapshotEntity`.
- **Store**: Create `RunSnapshotStore`.
- **Service**: Create `RunSnapshotService` with `createSnapshot(RunEntity run)` and `performSnapshotting()` logic.
- **Job**: Create `RunSnapshotJob` (Quartz) and register it in `ApplicationReadyEvent`.
- **Config**: Add `app.tasks.run-snapshot.cron` to `application.yaml`.

## 6. Testing
- **Unit Test**: Verify the binning logic with mock logs.
- **Integration Test**: Verify the job identifies runs and persists snapshots correctly.
- **Purge Test**: Verify that deleting a run after snapshotting does not affect the snapshot.
