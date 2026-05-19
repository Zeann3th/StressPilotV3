# Run Snapshotting Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement a scheduled job that snapshots completed runs into a flat table with binned response time metrics per endpoint, plus a manual trigger API.

**Architecture:** Hexagonal Architecture. Core logic resides in `RunServiceImpl`. Infrastructure layer (Quartz job and JPA store) delegates to the Core layer. A flat `runs_snapshot` table stores all data including a JSON blob of metrics.

**Tech Stack:** Spring Boot, JPA, Quartz Scheduler, Jackson (JSON), Flyway (PostgreSQL).

---

### Task 4: Core Service - Aggregation Logic in `RunServiceImpl`

**Files:**
- Modify: `src/main/java/dev/zeann3th/stresspilot/core/services/runs/RunService.java`
- Modify: `src/main/java/dev/zeann3th/stresspilot/core/services/runs/RunServiceImpl.java`

- [ ] **Step 1: Update `RunService` interface**
Add `void performSnapshotting();` and `RunSnapshotEntity createManualSnapshot(String runId);`.

- [ ] **Step 2: Inject `RunSnapshotStore` and `ObjectMapper`**
Update `RunServiceImpl` to include these dependencies.

- [ ] **Step 3: Implement `performSnapshotting` and `createManualSnapshot`**
Implement the logic to fetch eligible runs and the private `createSnapshot(RunEntity run)` method which performs the 20-bin aggregation.
- Binning: 20 bins per endpoint.
- Store results as a JSON string in the `metrics` column.

- [ ] **Step 4: Commit**
```bash
git add src/main/java/dev/zeann3th/stresspilot/core/services/runs/
git commit -m "feat: implement snapshotting aggregation logic"
```

---

### Task 5: Infrastructure - Quartz Job & Config

**Files:**
- Create: `src/main/java/dev/zeann3th/stresspilot/infrastructure/adapters/jobs/RunSnapshotJob.java`
- Create: `src/main/java/dev/zeann3th/stresspilot/infrastructure/configs/SnapshotJobConfig.java`
- Modify: `src/main/resources/application.yaml`

- [ ] **Step 1: Create `RunSnapshotJob`**
Delegates directly to `runService.performSnapshotting()`.

- [ ] **Step 2: Create `SnapshotJobConfig`**
Configure Quartz `JobDetail` and `Trigger` using `application.tasks.run-snapshot.cron`.

- [ ] **Step 3: Update `application.yaml`**
Add default cron `0 0/10 * * * ?`.

- [ ] **Step 4: Commit**
```bash
git commit -m "feat: add scheduled snapshot job"
```

---

### Task 6: UI - Manual Trigger API

**Files:**
- Modify: `src/main/java/dev/zeann3th/stresspilot/ui/restful/RunController.java`

- [ ] **Step 1: Add POST endpoint**
Add `POST /api/v1/runs/{id}/snapshot` to trigger a manual snapshot.

- [ ] **Step 2: Commit**
```bash
git commit -m "feat: add manual snapshot trigger API"
```
