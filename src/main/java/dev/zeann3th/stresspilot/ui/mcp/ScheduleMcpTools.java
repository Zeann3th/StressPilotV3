package dev.zeann3th.stresspilot.ui.mcp;

import dev.zeann3th.stresspilot.core.domain.entities.ScheduleEntity;
import dev.zeann3th.stresspilot.core.services.jobs.ScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class ScheduleMcpTools {

    private final ScheduleService scheduleService;

    @Tool(description = "List all scheduled flow runs")
    public Page<ScheduleEntity> listSchedules() {
        return scheduleService.getListSchedule(PageRequest.of(0, 100));
    }

    @Tool(description = "Get detailed information about a schedule")
    public ScheduleEntity getScheduleDetail(
            @ToolParam(description = "Schedule ID") Long id) {
        return scheduleService.getScheduleDetail(id);
    }

    @Tool(description = "Create a new schedule. Example JSON: { \"flowId\": 1, \"quartzExpr\": \"0 0 12 * * ?\", \"enabled\": true, \"threads\": 10 }")
    public ScheduleEntity createSchedule(
            @ToolParam(description = "Schedule entity data") ScheduleEntity entity) {
        return scheduleService.createSchedule(entity);
    }

    @Tool(description = "Update an existing schedule. Example JSON for patch: { \"enabled\": false, \"threads\": 5 }")
    public ScheduleEntity updateSchedule(
            @ToolParam(description = "Schedule ID") Long id,
            @ToolParam(description = "Patch data map") Map<String, Object> patch) {
        return scheduleService.updateSchedule(id, patch);
    }

    @Tool(description = "Delete a schedule")
    public void deleteSchedule(
            @ToolParam(description = "Schedule ID") Long id) {
        scheduleService.deleteSchedule(id);
    }
}
