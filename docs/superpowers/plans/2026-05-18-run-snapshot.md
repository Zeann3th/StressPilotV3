# Run Snapshotting Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement a scheduled job that snapshots completed runs into a flat table with binned response time metrics per endpoint.

**Architecture:** Hexagonal Architecture. Core logic resides in `RunServiceImpl`. Infrastructure layer (Quartz job and JPA store) delegates to the Core layer. A flat `runs_snapshot` table stores all data including a JSON blob of metrics to ensure independence from original logs.

**Tech Stack:** Spring Boot, JPA, Quartz Scheduler, Jackson (JSON), Flyway (PostgreSQL).

---

### Task 1: Database Migration

**Files:**
- Create: `src/main/resources/db/postgres/migrations/V11__add_runs_snapshot.sql`

- [ ] **Step 1: Create Flyway migration for `runs_snapshot` table**
```sql
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
```

- [ ] **Step 2: Commit**
```bash
git add src/main/resources/db/postgres/migrations/V11__add_runs_snapshot.sql
git commit -m "db: add runs_snapshot table"
```

---

### Task 2: Core Domain - Snapshot Entity

**Files:**
- Create: `src/main/java/dev/zeann3th/stresspilot/core/domain/entities/RunSnapshotEntity.java`

- [ ] **Step 1: Implement `RunSnapshotEntity`**
```java
package dev.zeann3th.stresspilot.core.domain.entities;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "runs_snapshot")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RunSnapshotEntity {
    @Id
    @Column(name = "id", length = 20, nullable = false, updatable = false)
    private String id;

    @Column(name = "flow_id", nullable = false)
    private Long flowId;

    @Column(name = "status", length = 10, nullable = false)
    private String status;

    @Column(name = "threads", nullable = false)
    private Integer threads;

    @Column(name = "duration", nullable = false)
    private Integer duration;

    @Column(name = "ramp_up_duration", nullable = false)
    private Integer rampUpDuration;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "completed_at", nullable = false)
    private LocalDateTime completedAt;

    @Column(name = "metrics", columnDefinition = "TEXT", nullable = false)
    private String metrics;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
```

- [ ] **Step 2: Commit**
```bash
git add src/main/java/dev/zeann3th/stresspilot/core/domain/entities/RunSnapshotEntity.java
git commit -m "feat: add RunSnapshotEntity"
```

---

### Task 3: Core Ports & Infrastructure Adapters - Store

**Files:**
- Create: `src/main/java/dev/zeann3th/stresspilot/core/ports/store/RunSnapshotStore.java`
- Create: `src/main/java/dev/zeann3th/stresspilot/infrastructure/adapters/store/RunSnapshotStoreImpl.java`
- Modify: `src/main/java/dev/zeann3th/stresspilot/core/ports/store/RunStore.java` (Add `findEligibleForSnapshotting`)

- [ ] **Step 1: Create `RunSnapshotStore` port**
```java
package dev.zeann3th.stresspilot.core.ports.store;

import dev.zeann3th.stresspilot.core.domain.entities.RunSnapshotEntity;
import java.util.Optional;

public interface RunSnapshotStore {
    RunSnapshotEntity save(RunSnapshotEntity snapshot);
    boolean existsById(String id);
}
```

- [ ] **Step 2: Add query to `RunStore` to find runs not yet snapshotted**
```java
// src/main/java/dev/zeann3th/stresspilot/core/ports/store/RunStore.java
List<RunEntity> findCompletedWithoutSnapshot(int limit);
```

- [ ] **Step 3: Implement `RunSnapshotStoreImpl`**
(Requires creating a JpaRepository first, but for brevity we use the implementation pattern of the project)
```java
// Note: Create RunSnapshotRepository in infrastructure first
```

- [ ] **Step 4: Commit**
```bash
git commit -m "feat: add RunSnapshotStore port and adapter"
```

---

### Task 4: Core Service - Aggregation Logic in `RunServiceImpl`

**Files:**
- Modify: `src/main/java/dev/zeann3th/stresspilot/core/services/runs/RunService.java`
- Modify: `src/main/java/dev/zeann3th/stresspilot/core/services/runs/RunServiceImpl.java`

- [ ] **Step 1: Add `performSnapshotting` to `RunService`**
```java
void performSnapshotting();
```

- [ ] **Step 2: Implement 20-bin aggregation and snapshot persistence in `RunServiceImpl`**
- Use Jackson `ObjectMapper` to serialize metrics.
- Group `request_logs` by `endpointId` and time bin.

```java
@Transactional
public void performSnapshotting() {
    List<RunEntity> eligibleRuns = runStore.findCompletedWithoutSnapshot(10);
    for (RunEntity run : eligibleRuns) {
        if (!runSnapshotStore.existsById(run.getId())) {
            createSnapshot(run);
        }
    }
}

private void createSnapshot(RunEntity run) {
    // 1. Calculate bins
    // 2. Aggregate logs
    // 3. Save RunSnapshotEntity
}
```

- [ ] **Step 3: Commit**
```bash
git commit -m "feat: implement snapshotting logic in RunServiceImpl"
```

---

### Task 5: Infrastructure - Quartz Job

**Files:**
- Create: `src/main/java/dev/zeann3th/stresspilot/infrastructure/adapters/jobs/RunSnapshotJob.java`
- Modify: `src/main/java/dev/zeann3th/stresspilot/core/services/jobs/ScheduleServiceImpl.java` (Register the system job)

- [ ] **Step 1: Create `RunSnapshotJob`**
```java
package dev.zeann3th.stresspilot.infrastructure.adapters.jobs;

import dev.zeann3th.stresspilot.core.services.runs.RunService;
import lombok.RequiredArgsConstructor;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RunSnapshotJob implements Job {
    private final RunService runService;

    @Override
    public void execute(JobExecutionContext context) {
        runService.performSnapshotting();
    }
}
```

- [ ] **Step 2: Add cron to `application.yaml`**
```yaml
application:
  tasks:
    run-snapshot:
      cron: "0 0/10 * * * ?"
```

- [ ] **Step 3: Commit**
```bash
git commit -m "feat: add RunSnapshotJob and configuration"
```
