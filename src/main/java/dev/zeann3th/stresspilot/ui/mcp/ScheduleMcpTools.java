package dev.zeann3th.stresspilot.ui.mcp;

import dev.zeann3th.stresspilot.core.services.jobs.ScheduleService;
import dev.zeann3th.stresspilot.ui.restful.dtos.schedule.CreateScheduleRequestDTO;
import dev.zeann3th.stresspilot.ui.restful.dtos.schedule.ScheduleResponseDTO;
import dev.zeann3th.stresspilot.ui.restful.mappers.ScheduleMapper;
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
    private final ScheduleMapper scheduleMapper;

    @McpTool(description = "List all scheduled flow runs", generateOutputSchema = true)
    public Page<ScheduleResponseDTO> listSchedules() {
        return scheduleService.getListSchedule(PageRequest.of(0, 100))
                .map(scheduleMapper::toResponse);
    }

    @McpTool(description = "Get detailed information about a schedule", generateOutputSchema = true)
    public ScheduleResponseDTO getScheduleDetail(
            @McpToolParam(description = "Schedule ID") Long id) {
        return scheduleMapper.toResponse(scheduleService.getScheduleDetail(id));
    }

    @McpTool(description = "Create a new schedule. Example JSON: { \"flowId\": 1, \"quartzExpr\": \"0 0 12 * * ?\", \"enabled\": true, \"threads\": 10 }", generateOutputSchema = true)
    public ScheduleResponseDTO createSchedule(
            @McpToolParam(description = "Schedule request data") CreateScheduleRequestDTO request) {
        return scheduleMapper.toResponse(scheduleService.createSchedule(scheduleMapper.toEntity(request)));
    }

    @McpTool(description = "Update an existing schedule. Example JSON for patch: { \"enabled\": false, \"threads\": 5 }", generateOutputSchema = true)
    public ScheduleResponseDTO updateSchedule(
            @McpToolParam(description = "Schedule ID") Long id,
            @McpToolParam(description = "Patch data map") Map<String, Object> patch) {
        return scheduleMapper.toResponse(scheduleService.updateSchedule(id, patch));
    }

    @McpTool(description = "Delete a schedule")
    public void deleteSchedule(
            @McpToolParam(description = "Schedule ID") Long id) {
        scheduleService.deleteSchedule(id);
    }
}
