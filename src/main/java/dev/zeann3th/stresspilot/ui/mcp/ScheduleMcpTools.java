package dev.zeann3th.stresspilot.ui.mcp;

import dev.zeann3th.stresspilot.core.domain.entities.ScheduleEntity;
import dev.zeann3th.stresspilot.core.services.jobs.ScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class ScheduleMcpTools {

    private final ScheduleService scheduleService;

    @McpTool(description = "List all scheduled flow runs")
    public Page<ScheduleEntity> listSchedules() {
        return scheduleService.getListSchedule(PageRequest.of(0, 100));
    }

    @McpTool(description = "Get detailed information about a schedule")
    public ScheduleEntity getScheduleDetail(
            @McpToolParam(description = "Schedule ID") Long id) {
        return scheduleService.getScheduleDetail(id);
    }

    @McpTool(description = "Create a new schedule. Example JSON: { \"flowId\": 1, \"quartzExpr\": \"0 0 12 * * ?\", \"enabled\": true, \"threads\": 10 }")
    public ScheduleEntity createSchedule(
            @McpToolParam(description = "Schedule entity data") ScheduleEntity entity) {
        return scheduleService.createSchedule(entity);
    }

    @McpTool(description = "Update an existing schedule. Example JSON for patch: { \"enabled\": false, \"threads\": 5 }")
    public ScheduleEntity updateSchedule(
            @McpToolParam(description = "Schedule ID") Long id,
            @McpToolParam(description = "Patch data map") Map<String, Object> patch) {
        return scheduleService.updateSchedule(id, patch);
    }

    @McpTool(description = "Delete a schedule")
    public void deleteSchedule(
            @McpToolParam(description = "Schedule ID") Long id) {
        scheduleService.deleteSchedule(id);
    }
}
