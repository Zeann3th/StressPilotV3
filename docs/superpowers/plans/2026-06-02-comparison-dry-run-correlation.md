# Comparison Charts, Dry Run, and Correlation Reports Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add comparison report charts, one-step flow dry run, and correlation ID report coverage/fixes.

**Architecture:** Backend changes extend the existing report generators and flow service rather than introducing parallel execution logic. Dry run reuses `FlowNodeHandler` dispatch with a non-persisting execution context.

**Tech Stack:** Spring Boot Java backend, Apache POI Excel generation, Flutter/Dart desktop frontend, JUnit/AssertJ, Flutter tests where feasible.

---

## File Structure

- Modify `src/main/java/dev/zeann3th/stresspilot/core/utils/RunComparisonExcelGenerator.java`: collect per-run time buckets and add a `Charts` sheet with two-run series.
- Modify `src/test/java/dev/zeann3th/stresspilot/core/utils/ExcelGeneratorTest.java` or create `RunComparisonExcelGeneratorTest.java`: verify comparison chart count and legends.
- Modify `src/main/java/dev/zeann3th/stresspilot/core/services/flows/FlowExecutionContext.java`: add request-log persistence flag.
- Modify `src/main/java/dev/zeann3th/stresspilot/core/services/flows/nodes/strategies/EndpointNodeHandler.java`: skip request-log queue/publish when persistence is disabled.
- Create `src/main/java/dev/zeann3th/stresspilot/core/domain/commands/flow/DryRunStepCommand.java`: dry-run command.
- Create `src/main/java/dev/zeann3th/stresspilot/core/domain/commands/flow/DryRunStepResult.java`: dry-run result.
- Modify `src/main/java/dev/zeann3th/stresspilot/core/services/flows/FlowService.java`: add dry-run method.
- Modify `src/main/java/dev/zeann3th/stresspilot/core/services/flows/FlowServiceImpl.java`: implement one-step dry run.
- Create `src/main/java/dev/zeann3th/stresspilot/ui/restful/dtos/flow/DryRunStepRequestDTO.java`: REST request.
- Create `src/main/java/dev/zeann3th/stresspilot/ui/restful/dtos/flow/DryRunStepResponseDTO.java`: REST response.
- Modify `src/main/java/dev/zeann3th/stresspilot/ui/restful/mappers/FlowMapper.java`: map dry-run command/result.
- Modify `src/main/java/dev/zeann3th/stresspilot/ui/restful/FlowController.java`: expose dry-run endpoint.
- Add backend tests under `src/test/java/dev/zeann3th/stresspilot/core/services/flows` and `src/test/java/dev/zeann3th/stresspilot/core/utils`.
- Modify `../stresspilot_super_app/lib/features/projects/domain/models/flow.dart`: add dry-run request/response models.
- Modify `../stresspilot_super_app/lib/features/projects/domain/repositories/flow_repository.dart`: add dry-run API.
- Modify `../stresspilot_super_app/lib/features/projects/data/repositories/flow_repository_impl.dart`: call backend dry-run endpoint.
- Modify `../stresspilot_super_app/lib/features/projects/presentation/provider/flow_provider.dart`: store dry-run temporary variables and expose action.
- Modify flow editor widgets only if there is an existing selected-step control surface with a small safe hook.

## Task 1: Comparison Excel Charts

- [ ] Add failing comparison generator test with two reports and logs. Assert `Charts` exists, has seven line charts, and legend series titles contain both run IDs.
- [ ] Run `.\mvnw.cmd -Dtest=RunComparisonExcelGeneratorTest test`; expected failure: no `Charts` sheet.
- [ ] Implement chart bucket collection in `RunComparisonExcelGenerator.appendRunDetailRow`.
- [ ] Add `addTimeSeriesCharts`, chart data writer, and chart creation helpers mirroring `ExcelGenerator` with Run A/Run B-prefixed series.
- [ ] Run `.\mvnw.cmd -Dtest=RunComparisonExcelGeneratorTest test`; expected pass.

## Task 2: Correlation ID Export Regression

- [ ] Add Excel test asserting `Detailed Logs` row correlation ID equals a supplied value.
- [ ] Add HTML test asserting detailed logs contain the supplied correlation ID.
- [ ] Run `.\mvnw.cmd -Dtest=ExcelGeneratorTest,HtmlReportGeneratorTest test`; expected failure if current export drops correlation ID.
- [ ] Fix the first broken mapping/rendering path, expected candidates: `HtmlReportGenerator` detailed log columns or report DTO usage.
- [ ] Re-run the two tests; expected pass.

## Task 3: Backend Dry Run

- [ ] Add command/result and REST DTO classes.
- [ ] Add failing service test that dry-runs one endpoint step and verifies no request log persistence.
- [ ] Add failing service test that dry-run temporary variables are returned after processor execution.
- [ ] Run the dry-run service tests; expected failure: method/classes missing.
- [ ] Add `persistRequestLogs` to `FlowExecutionContext` defaulting to true.
- [ ] Guard `EndpointNodeHandler` logging with `context.isPersistRequestLogs()`.
- [ ] Implement `FlowService.dryRunStep` in `FlowServiceImpl`, using loaded steps, active/override environment, `FlowProcessor`, `FlowNodeHandlerFactory`, and generated correlation ID.
- [ ] Add controller endpoint `POST /api/v1/flows/{flowId}/dry-run-step`.
- [ ] Run dry-run backend tests; expected pass.

## Task 4: Flutter Dry Run API and State

- [ ] Add Dart dry-run models to `flow.dart`.
- [ ] Extend `FlowRepository` and `FlowRepositoryImpl` with `dryRunStep`.
- [ ] Add provider state: `dryRunVariablesByFlowId`, `lastDryRunResult`, `isDryRunning`.
- [ ] Add provider method `dryRunStep(flowId, stepId, environmentId)` that sends stored temp variables and saves returned variables.
- [ ] Add a narrow UI action in the existing flow editor/selected node surface if the selected node API is clear; otherwise leave provider/API ready and document follow-up UI hook.
- [ ] Run `dart format lib/features/projects`.

## Task 5: Verification

- [ ] Run targeted backend tests for comparison charts, correlation exports, and dry run.
- [ ] Run `flutter analyze` for the app if frontend files changed.
- [ ] Run targeted Flutter tests if existing tests cover providers/models.
- [ ] Review `git diff` for unrelated changes before final response.
