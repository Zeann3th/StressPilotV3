# Distributed Flow And Backend Args Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a Redis-backed `DISTRIBUTED` flow mode and a Flutter settings panel for custom backend launch arguments.

**Architecture:** The backend keeps normal run persistence on the node that starts the run, then coordinates workers through one Redis Pub/Sub namespace rooted at `stresspilot:*`. Worker nodes execute assigned thread ranges and publish request-log messages back to the master; the master is the only node that queues DB request logs for distributed runs. The Flutter desktop app stores extra backend args locally and appends them to the Java command after the default profile argument.

**Tech Stack:** Spring Boot 4, Spring Data Redis/Lettuce, Jackson JSON mapper, JUnit/Mockito, Flutter, Provider, SharedPreferences.

---

## File Structure

Backend repository: `stresspilot/`

- Modify `pom.xml`: add `spring-boot-starter-data-redis`.
- Modify `src/main/resources/application.yaml`: add disabled-by-default distributed config with `stresspilot` key prefix.
- Modify `src/main/java/dev/zeann3th/stresspilot/core/domain/enums/FlowType.java`: add `DISTRIBUTED`.
- Modify `src/main/java/dev/zeann3th/stresspilot/core/services/ActiveRunRegistry.java`: expose active run lookup for local and distributed stop handling.
- Modify `src/main/java/dev/zeann3th/stresspilot/core/services/flows/FlowExecutionContext.java`: add a request-log routing mode flag.
- Modify `src/main/java/dev/zeann3th/stresspilot/core/services/flows/nodes/strategies/EndpointNodeHandler.java`: route logs locally or through a distributed publisher.
- Create `src/main/java/dev/zeann3th/stresspilot/infrastructure/configs/properties/DistributedFlowProperties.java`: typed config for Redis distributed mode.
- Create `src/main/java/dev/zeann3th/stresspilot/infrastructure/configs/DistributedRedisConfig.java`: conditional Redis beans and channel topic names.
- Create `src/main/java/dev/zeann3th/stresspilot/core/services/flows/distributed/DistributedChannels.java`: constants for `stresspilot:*` channel/key names.
- Create `src/main/java/dev/zeann3th/stresspilot/core/services/flows/distributed/DistributedEventPublisher.java`: publishes workload, stop, heartbeat, and request-log events.
- Create `src/main/java/dev/zeann3th/stresspilot/core/services/flows/distributed/DistributedWorkerRegistry.java`: tracks worker heartbeats in Redis keys under `stresspilot:workers:*`.
- Create `src/main/java/dev/zeann3th/stresspilot/core/services/flows/distributed/DistributedFlowExecutor.java`: master executor for `DISTRIBUTED`.
- Create `src/main/java/dev/zeann3th/stresspilot/core/services/flows/distributed/DistributedWorkerSubscriber.java`: worker subscriber that executes assigned workload.
- Create `src/main/java/dev/zeann3th/stresspilot/core/services/flows/distributed/DistributedMasterLogSubscriber.java`: master-side subscriber for worker request logs.
- Modify `src/main/java/dev/zeann3th/stresspilot/core/services/flows/FlowServiceImpl.java`: on stop, publish distributed stop when enabled.

Flutter repository: `stresspilot_super_app/`

- Create `lib/core/system/backend_launch_args.dart`: parses and persists custom backend args.
- Modify `lib/core/system/process_manager.dart`: load custom backend args and append them to `Process.start`.
- Modify `lib/core/di/locator.dart`: register backend args service.
- Modify `lib/features/settings/presentation/provider/setting_provider.dart`: expose backend args state and save/reset methods.
- Create `lib/features/settings/presentation/widgets/backend_args_settings_view.dart`: settings UI for backend args.
- Modify `lib/features/settings/presentation/widgets/settings_table.dart`: add `BACKEND` category.

## Task 1: Backend Redis Dependency And Config

**Files:**
- Modify: `stresspilot/pom.xml`
- Modify: `stresspilot/src/main/resources/application.yaml`
- Create: `stresspilot/src/main/java/dev/zeann3th/stresspilot/infrastructure/configs/properties/DistributedFlowProperties.java`
- Create: `stresspilot/src/main/java/dev/zeann3th/stresspilot/infrastructure/configs/DistributedRedisConfig.java`
- Test: `stresspilot/src/test/java/dev/zeann3th/stresspilot/infrastructure/configs/DistributedRedisConfigTest.java`

- [ ] **Step 1: Write failing config test**

```java
package dev.zeann3th.stresspilot.infrastructure.configs;

import dev.zeann3th.stresspilot.infrastructure.configs.properties.DistributedFlowProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DistributedRedisConfigTest {
    @Test
    void distributedConfigDefaultsToDisabledAndStresspilotPrefix() {
        DistributedFlowProperties properties = new DistributedFlowProperties();

        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.getKeyPrefix()).isEqualTo("stresspilot");
        assertThat(properties.getWorkerTtlSeconds()).isEqualTo(15);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\mvnw.cmd -Dtest=DistributedRedisConfigTest test`

Expected: compile failure because `DistributedFlowProperties` does not exist.

- [ ] **Step 3: Add dependency and typed properties**

Add to `pom.xml` dependencies:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

Create `DistributedFlowProperties`:

```java
package dev.zeann3th.stresspilot.infrastructure.configs.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "application.distributed")
public class DistributedFlowProperties {
    private boolean enabled = false;
    private String keyPrefix = "stresspilot";
    private String nodeId = java.util.UUID.randomUUID().toString();
    private int workerTtlSeconds = 15;
    private int workerHeartbeatSeconds = 5;
    private int workerDiscoveryTimeoutMs = 2000;
}
```

Create conditional config:

```java
package dev.zeann3th.stresspilot.infrastructure.configs;

import dev.zeann3th.stresspilot.infrastructure.configs.properties.DistributedFlowProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(DistributedFlowProperties.class)
@ConditionalOnProperty(prefix = "application.distributed", name = "enabled", havingValue = "true")
public class DistributedRedisConfig {
}
```

Add to `application.yaml`:

```yaml
application:
  distributed:
    enabled: false
    key-prefix: stresspilot
    node-id: ${HOSTNAME:${COMPUTERNAME:local}}
    worker-ttl-seconds: 15
    worker-heartbeat-seconds: 5
    worker-discovery-timeout-ms: 2000
```

- [ ] **Step 4: Run test to verify it passes**

Run: `.\mvnw.cmd -Dtest=DistributedRedisConfigTest test`

Expected: test passes.

## Task 2: Distributed Channels And Worker Presence

**Files:**
- Create: `stresspilot/src/main/java/dev/zeann3th/stresspilot/core/services/flows/distributed/DistributedChannels.java`
- Create: `stresspilot/src/main/java/dev/zeann3th/stresspilot/core/services/flows/distributed/DistributedWorkerRegistry.java`
- Test: `stresspilot/src/test/java/dev/zeann3th/stresspilot/core/services/flows/distributed/DistributedChannelsTest.java`

- [ ] **Step 1: Write failing channel test**

```java
package dev.zeann3th.stresspilot.core.services.flows.distributed;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DistributedChannelsTest {
    @Test
    void allChannelsAndKeysUseStresspilotPrefix() {
        DistributedChannels channels = new DistributedChannels("stresspilot");

        assertThat(channels.workerHeartbeatChannel()).isEqualTo("stresspilot:distributed:worker:heartbeat");
        assertThat(channels.workChannel()).isEqualTo("stresspilot:distributed:work");
        assertThat(channels.stopChannel()).isEqualTo("stresspilot:distributed:stop");
        assertThat(channels.requestLogChannel()).isEqualTo("stresspilot:distributed:request-log");
        assertThat(channels.workerKey("node-1")).isEqualTo("stresspilot:workers:node-1");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\mvnw.cmd -Dtest=DistributedChannelsTest test`

Expected: compile failure because `DistributedChannels` does not exist.

- [ ] **Step 3: Implement channel constants**

```java
package dev.zeann3th.stresspilot.core.services.flows.distributed;

public record DistributedChannels(String prefix) {
    public String workerHeartbeatChannel() { return prefix + ":distributed:worker:heartbeat"; }
    public String workChannel() { return prefix + ":distributed:work"; }
    public String stopChannel() { return prefix + ":distributed:stop"; }
    public String requestLogChannel() { return prefix + ":distributed:request-log"; }
    public String workerKey(String nodeId) { return prefix + ":workers:" + nodeId; }
    public String runKey(String runId) { return prefix + ":runs:" + runId; }
}
```

- [ ] **Step 4: Implement worker registry**

Use `StringRedisTemplate` to set heartbeat keys with TTL and scan keys matching `stresspilot:workers:*`. Return node ids by stripping the prefix.

- [ ] **Step 5: Run test to verify it passes**

Run: `.\mvnw.cmd -Dtest=DistributedChannelsTest test`

Expected: test passes.

## Task 3: Distributed Request Log Routing

**Files:**
- Modify: `stresspilot/src/main/java/dev/zeann3th/stresspilot/core/services/flows/FlowExecutionContext.java`
- Modify: `stresspilot/src/main/java/dev/zeann3th/stresspilot/core/services/flows/nodes/strategies/EndpointNodeHandler.java`
- Create: `stresspilot/src/main/java/dev/zeann3th/stresspilot/core/services/flows/distributed/DistributedEventPublisher.java`
- Create: `stresspilot/src/main/java/dev/zeann3th/stresspilot/core/services/flows/distributed/DistributedMasterLogSubscriber.java`
- Test: `stresspilot/src/test/java/dev/zeann3th/stresspilot/core/services/flows/nodes/strategies/EndpointNodeHandlerTest.java`

- [ ] **Step 1: Add failing endpoint handler test**

Add a test where `FlowExecutionContext` has distributed log routing enabled, then assert `RequestLogService.queueLog` is not called and `DistributedEventPublisher.publishRequestLog` is called once.

- [ ] **Step 2: Run test to verify it fails**

Run: `.\mvnw.cmd -Dtest=EndpointNodeHandlerTest test`

Expected: compile failure until distributed publisher API exists.

- [ ] **Step 3: Add routing flag and publisher**

Add `@Builder.Default private boolean distributedWorker = false;` to `FlowExecutionContext`.

Add a publisher method:

```java
public void publishRequestLog(RequestLogEntity log) {
    redisTemplate.convertAndSend(channels.requestLogChannel(), jsonMapper.writeValueAsString(logMessage));
}
```

Use a DTO containing scalar IDs and log fields, not the JPA entity graph.

- [ ] **Step 4: Change endpoint logging branch**

In `EndpointNodeHandler`, keep JS suppression. For recordable endpoints:

```java
RequestLogEntity log = RequestLogEntity.builder()
        .run(context.getRun())
        .endpoint(endpoint)
        .statusCode(result.getStatusCode())
        .success(result.isSuccess())
        .responseTime(result.getResponseTimeMs())
        .request(requestDebug.toString())
        .response(responseText)
        .createdAt(LocalDateTime.now())
        .build();
if (context.isDistributedWorker()) {
    distributedEventPublisher.publishRequestLog(log);
} else {
    requestLogService.queueLog(log);
}
```

- [ ] **Step 5: Implement master log subscriber**

Deserialize request-log DTOs and reconstruct `RequestLogEntity` with `RunEntity.builder().id(runId).build()` and `EndpointEntity.builder().id(endpointId).build()`, then call `RequestLogService.queueLog`.

- [ ] **Step 6: Run endpoint tests**

Run: `.\mvnw.cmd -Dtest=EndpointNodeHandlerTest test`

Expected: existing JS/local tests and new distributed routing test pass.

## Task 4: Master Distributed Executor

**Files:**
- Modify: `stresspilot/src/main/java/dev/zeann3th/stresspilot/core/domain/enums/FlowType.java`
- Create: `stresspilot/src/main/java/dev/zeann3th/stresspilot/core/services/flows/distributed/DistributedFlowExecutor.java`
- Modify: `stresspilot/src/main/java/dev/zeann3th/stresspilot/core/services/flows/FlowExecutorFactory.java`
- Test: `stresspilot/src/test/java/dev/zeann3th/stresspilot/core/services/flows/distributed/DistributedFlowExecutorTest.java`

- [ ] **Step 1: Write failing executor test**

Test that `FlowType.DISTRIBUTED` is supported, `DistributedFlowExecutor.execute` publishes no work when no workers are present, and publishes two work messages with balanced thread counts when two workers are present and `threads=5`.

- [ ] **Step 2: Run test to verify it fails**

Run: `.\mvnw.cmd -Dtest=DistributedFlowExecutorTest test`

Expected: compile failure until executor exists.

- [ ] **Step 3: Implement minimal executor**

`DistributedFlowExecutor` should support `FlowType.DISTRIBUTED.name()`. It registers the run, gets workers from `DistributedWorkerRegistry`, splits threads as `[3,2]` for five threads over two workers, publishes work messages, waits until deadline or stop signal, then deregisters.

- [ ] **Step 4: Handle no-worker case**

If no workers are active, return `RunStatus.ABORTED.name()` and log a clear warning. Do not silently fall back to local execution.

- [ ] **Step 5: Run executor test**

Run: `.\mvnw.cmd -Dtest=DistributedFlowExecutorTest test`

Expected: test passes.

## Task 5: Worker Subscriber And Stop Propagation

**Files:**
- Modify: `stresspilot/src/main/java/dev/zeann3th/stresspilot/core/services/ActiveRunRegistry.java`
- Modify: `stresspilot/src/main/java/dev/zeann3th/stresspilot/core/services/flows/FlowServiceImpl.java`
- Create: `stresspilot/src/main/java/dev/zeann3th/stresspilot/core/services/flows/distributed/DistributedWorkerSubscriber.java`
- Test: `stresspilot/src/test/java/dev/zeann3th/stresspilot/core/services/flows/distributed/DistributedWorkerSubscriberTest.java`

- [ ] **Step 1: Write failing subscriber test**

Test that a work message registers the run, forks contexts with `distributedWorker=true`, invokes worker execution for the assigned thread count, and deregisters the run.

- [ ] **Step 2: Run test to verify it fails**

Run: `.\mvnw.cmd -Dtest=DistributedWorkerSubscriberTest test`

Expected: compile failure until subscriber exists.

- [ ] **Step 3: Implement work subscriber**

Subscribe to `stresspilot:distributed:work`. Ignore messages not assigned to this node id. For assigned work, recreate `FlowExecutionContext` from payload, set `distributedWorker=true`, and reuse default worker iteration behavior through a small shared worker runner extracted from `DefaultFlowExecutor` if necessary.

- [ ] **Step 4: Implement stop subscriber**

Subscribe to `stresspilot:distributed:stop` and call `ActiveRunRegistry.interruptRun(runId)`.

- [ ] **Step 5: Publish stop from master**

In `FlowServiceImpl.handleInterruptRunEvent`, after local interrupt attempt, call `DistributedEventPublisher.publishStop(runId)` when distributed is enabled.

- [ ] **Step 6: Run subscriber test**

Run: `.\mvnw.cmd -Dtest=DistributedWorkerSubscriberTest test`

Expected: test passes.

## Task 6: Flutter Backend Args Persistence

**Files:**
- Create: `stresspilot_super_app/lib/core/system/backend_launch_args.dart`
- Modify: `stresspilot_super_app/lib/core/di/locator.dart`
- Test: `stresspilot_super_app/test/backend_launch_args_test.dart`

- [ ] **Step 1: Write failing Dart test**

```dart
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:stress_pilot/core/system/backend_launch_args.dart';

void main() {
  test('stores and parses newline separated backend args', () async {
    SharedPreferences.setMockInitialValues({});
    final service = BackendLaunchArgs();

    await service.saveRaw('--application.distributed.enabled=true\n--spring.data.redis.host=127.0.0.1');

    expect(await service.loadArgs(), [
      '--application.distributed.enabled=true',
      '--spring.data.redis.host=127.0.0.1',
    ]);
  });
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `flutter test test/backend_launch_args_test.dart`

Expected: compile failure because `BackendLaunchArgs` does not exist.

- [ ] **Step 3: Implement service**

Create a service with `loadRaw`, `saveRaw`, `reset`, and `loadArgs`. Split by newline, trim whitespace, discard blank lines, and do not shell-parse quotes because `Process.start` receives a list of arguments directly.

- [ ] **Step 4: Register service**

In `locator.dart`, add `getIt.registerLazySingleton(() => BackendLaunchArgs());`.

- [ ] **Step 5: Run test**

Run: `flutter test test/backend_launch_args_test.dart`

Expected: test passes.

## Task 7: Flutter ProcessManager Uses Custom Args

**Files:**
- Modify: `stresspilot_super_app/lib/core/system/process_manager.dart`
- Test: `stresspilot_super_app/test/process_manager_args_test.dart`

- [ ] **Step 1: Write failing unit test around argument composition**

Extract a pure method that builds backend args:

```dart
List<String> buildBackendArgs({
  required String jarPath,
  required String profile,
  required String? jsaPath,
  required List<String> customArgs,
})
```

Test that custom args are appended after `--spring.profiles.active=dev`.

- [ ] **Step 2: Run test to verify it fails**

Run: `flutter test test/process_manager_args_test.dart`

Expected: compile failure until helper exists.

- [ ] **Step 3: Use backend args in startBackend**

Inject or read `BackendLaunchArgs` from `getIt`, call `loadArgs()`, and append them to the existing Java args list. Log the full arg count but do not log values that may contain credentials.

- [ ] **Step 4: Run test**

Run: `flutter test test/process_manager_args_test.dart`

Expected: test passes.

## Task 8: Flutter Settings UI For Backend Args

**Files:**
- Modify: `stresspilot_super_app/lib/features/settings/presentation/provider/setting_provider.dart`
- Create: `stresspilot_super_app/lib/features/settings/presentation/widgets/backend_args_settings_view.dart`
- Modify: `stresspilot_super_app/lib/features/settings/presentation/widgets/settings_table.dart`
- Test: `stresspilot_super_app/test/backend_args_settings_view_test.dart`

- [ ] **Step 1: Write failing widget test**

Test that the backend settings view shows a multiline field, saves text to `BackendLaunchArgs`, and displays restart-required copy already consistent with `SettingsRow`.

- [ ] **Step 2: Run test to verify it fails**

Run: `flutter test test/backend_args_settings_view_test.dart`

Expected: compile failure until widget exists.

- [ ] **Step 3: Add provider methods**

Add `backendArgsRaw`, `loadBackendArgs`, `saveBackendArgs`, and `resetBackendArgs` to `SettingProvider`.

- [ ] **Step 4: Build backend settings view**

Use existing `PilotInput`, `PilotButton`, `PilotToast`, and settings layout style. The textarea placeholder should include:

```text
--application.distributed.enabled=true
--spring.data.redis.host=127.0.0.1
--spring.data.redis.port=6379
```

- [ ] **Step 5: Add BACKEND category**

In `SettingsTable`, add `BACKEND` to the static categories and route it to `BackendArgsSettingsView`.

- [ ] **Step 6: Run widget test**

Run: `flutter test test/backend_args_settings_view_test.dart`

Expected: test passes.

## Task 9: Focused Integration Verification

**Files:**
- Backend and Flutter files touched in prior tasks.

- [ ] **Step 1: Run backend focused tests**

Run: `.\mvnw.cmd -Dtest=DistributedRedisConfigTest,DistributedChannelsTest,EndpointNodeHandlerTest,DistributedFlowExecutorTest,DistributedWorkerSubscriberTest test`

Expected: all listed tests pass.

- [ ] **Step 2: Run backend compile**

Run: `.\mvnw.cmd -DskipTests compile`

Expected: build succeeds.

- [ ] **Step 3: Run Flutter focused tests**

Run: `flutter test test/backend_launch_args_test.dart test/process_manager_args_test.dart test/backend_args_settings_view_test.dart`

Expected: all listed tests pass.

- [ ] **Step 4: Run Flutter analyze**

Run: `flutter analyze`

Expected: no new analyzer errors from changed files.

## Self-Review

- Spec coverage: the plan covers `DISTRIBUTED` flow type creation, optional Redis dependency/config, single `stresspilot:*` namespace, master-only persistence, worker execution, distributed stop, conditional request-log routing, and Flutter custom backend args.
- Placeholder scan: no `TBD` or unspecified implementation placeholders remain; worker execution extraction is named as a concrete action in Task 5.
- Type consistency: backend uses `DistributedFlowProperties`, `DistributedChannels`, `DistributedEventPublisher`, and `BackendLaunchArgs` consistently across tasks.
