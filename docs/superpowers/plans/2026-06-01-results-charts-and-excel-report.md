# Results Charts And Excel Report Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Smooth the live results chart sliding window and add restrained, readable time-series charts to exported Excel run reports.

**Architecture:** Keep live chart data ownership in `ResultsProvider` and tune the `RealtimeChart` rendering so the 60-second window does not animate as a full redraw. Keep report tables unchanged in `ExcelGenerator`, add a derived endpoint TPS column, and place two time-series line charts beside the summary and endpoint aggregate tables using buckets collected from streamed detailed logs.

**Tech Stack:** Flutter, `fl_chart`, Java, Apache POI `poi-ooxml`, Maven.

---

### Task 1: Excel Report Charts

**Files:**
- Modify: `stresspilot/src/main/java/dev/zeann3th/stresspilot/core/utils/ExcelGenerator.java`
- Create: `stresspilot/src/test/java/dev/zeann3th/stresspilot/core/utils/ExcelGeneratorTest.java`

- [ ] **Step 1: Write failing test**

Add a unit test that writes a workbook to memory and verifies the Summary and Endpoint Aggregates sheets contain exactly two line charts each and the endpoint sheet keeps table columns while adding `TPS`.

- [ ] **Step 2: Run test to verify it fails**

Run: `.\mvnw.cmd -Dtest=ExcelGeneratorTest test`

- [ ] **Step 3: Implement minimal report changes**

Add `writeTo(OutputStream)` for testability, collect per-second buckets from `appendDetailRow`, create line charts using Apache POI XDDF APIs, keep existing data tables, add endpoint TPS as requests divided by report duration, and position charts in fixed readable areas.

- [ ] **Step 4: Run backend tests**

Run: `.\mvnw.cmd -Dtest=ExcelGeneratorTest test`

### Task 2: Flutter Sliding Window Chart

**Files:**
- Modify: `stresspilot_super_app/lib/features/results/presentation/widgets/realtime_chart.dart`

- [ ] **Step 1: Update chart rendering**

Disable the implicit fl_chart tween for the realtime line chart and slightly tighten axis/tooltip behavior so new points appear as a sliding time window instead of an animated redraw.

- [ ] **Step 2: Run Flutter verification**

Run: `flutter analyze`

### Task 3: Final Verification

**Files:**
- Verify modified backend and Flutter files only.

- [ ] **Step 1: Run focused checks**

Run backend unit test and Flutter analysis.

- [ ] **Step 2: Review diffs**

Confirm the report does not add excessive sheets or charts, and that raw columns remain available.
