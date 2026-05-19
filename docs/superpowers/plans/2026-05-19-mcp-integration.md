# MCP Server Integration Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement an MCP server using Spring AI to expose all core StressPilot APIs as tools for AI agents, using SSE transport and ensuring proper security configuration.

**Architecture:** UI layer extension. Create a new `dev.zeann3th.stresspilot.ui.mcp` package. Use annotation-based tool definitions (`@McpTool`) that delegate to existing core services.

**Tech Stack:** Java 25, Spring Boot, Spring AI MCP Server Starter, SSE Transport.

---

### Task 1: Dependencies

**Files:**
- Modify: `pom.xml`

- [ ] **Step 1: Add Spring AI BOM to `dependencyManagement`**
```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-bom</artifactId>
    <version>1.0.0-M7</version>
    <type>pom</type>
    <scope>import</scope>
</dependency>
```

- [ ] **Step 2: Add MCP Server Starter dependency**
```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-mcp-server-webmvc-spring-boot-starter</artifactId>
</dependency>
```

- [ ] **Step 3: Commit**
```bash
git add pom.xml
git commit -m "build: add Spring AI MCP dependencies"
```

---

### Task 2: Configuration & Security

**Files:**
- Modify: `src/main/resources/application.yaml`
- Modify: `src/main/java/dev/zeann3th/stresspilot/core/domain/constants/Constants.java`

- [ ] **Step 1: Configure SSE transport in `application.yaml`**
```yaml
spring:
  ai:
    mcp:
      server:
        transport: SSE
        sse:
          path: /mcp/sse
        message:
          path: /mcp/message
```

- [ ] **Step 2: Add MCP paths to `Constants.PUBLIC_PATHS`**
Update `PUBLIC_PATHS` to include `"/mcp/sse"` and `"/mcp/message"`.

- [ ] **Step 3: Commit**
```bash
git add src/main/resources/application.yaml src/main/java/dev/zeann3th/stresspilot/core/domain/constants/Constants.java
git commit -m "config: configure MCP SSE transport and public paths"
```

---

### Task 3: MCP Tool Provider - Project, Environment & Config

**Files:**
- Create: `src/main/java/dev/zeann3th/stresspilot/ui/mcp/ProjectMcpTools.java`
- Create: `src/main/java/dev/zeann3th/stresspilot/ui/mcp/EnvironmentMcpTools.java`
- Create: `src/main/java/dev/zeann3th/stresspilot/ui/mcp/ConfigMcpTools.java`

- [ ] **Step 1: Implement `ProjectMcpTools`**
Expose methods from `ProjectService`.
- `listProjects()`, `getProjectDetail(Long id)`, `createProject(...)`, `updateProject(...)`, `deleteProject(Long id)`.

- [ ] **Step 2: Implement `EnvironmentMcpTools`**
Expose methods from `EnvironmentService`.
- `listEnvironments()`, `getEnvironmentDetail(Long id)`, etc.

- [ ] **Step 3: Implement `ConfigMcpTools`**
Expose methods from `ConfigService`.

- [ ] **Step 4: Commit**
```bash
git add src/main/java/dev/zeann3th/stresspilot/ui/mcp/
git commit -m "feat: add Project, Environment and Config MCP tools"
```

---

### Task 4: MCP Tool Provider - Flow, Endpoint & Run

**Files:**
- Create: `src/main/java/dev/zeann3th/stresspilot/ui/mcp/FlowMcpTools.java`
- Create: `src/main/java/dev/zeann3th/stresspilot/ui/mcp/EndpointMcpTools.java`
- Create: `src/main/java/dev/zeann3th/stresspilot/ui/mcp/RunMcpTools.java`

- [ ] **Step 1: Implement `FlowMcpTools`**
Expose methods from `FlowService`.
- `listFlows(Long projectId)`, `getFlowDetail(Long id)`, `runFlow(Long flowId, RunFlowCommand cmd)`.

- [ ] **Step 2: Implement `EndpointMcpTools`**
Expose methods from `EndpointService`.

- [ ] **Step 3: Implement `RunMcpTools`**
Expose methods from `RunService`.
- `getRunHistory(Long flowId)`, `getRunDetail(String runId)`, `createManualSnapshot(String runId)`, `compareSnapshots(String runId1, String runId2)`.

- [ ] **Step 4: Commit**
```bash
git commit -m "feat: add Flow, Endpoint and Run MCP tools"
```

---

### Task 5: Final Verification

- [ ] **Step 1: Run build and verify tools are registered**
- [ ] **Step 2: Commit**
```bash
git commit -m "docs: complete MCP integration"
```
