package dev.zeann3th.stresspilot.ui.restful;

import dev.zeann3th.stresspilot.core.domain.entities.ScheduleEntity;
import dev.zeann3th.stresspilot.core.services.jobs.ScheduleService;
import dev.zeann3th.stresspilot.ui.restful.dtos.schedule.CreateScheduleRequestDTO;
import dev.zeann3th.stresspilot.ui.restful.dtos.schedule.ScheduleResponseDTO;
import dev.zeann3th.stresspilot.ui.restful.exception.ResponseWrapper;
import dev.zeann3th.stresspilot.ui.restful.mappers.ScheduleMapper;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/schedules")
@RequiredArgsConstructor
@ResponseWrapper
@Tag(name = "Schedules", description = "API for managing scheduled flow runs")
public class ScheduleController {

    private final ScheduleService scheduleService;
    private final ScheduleMapper scheduleMapper;

    @GetMapping
    public Page<ScheduleResponseDTO> getListSchedule(
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        Page<ScheduleEntity> resp = scheduleService.getListSchedule(pageable);
        return resp.map(scheduleMapper::toResponse);
    }

    @GetMapping("/{id}")
    public ScheduleResponseDTO getScheduleDetail(@PathVariable Long id) {
        ScheduleEntity resp = scheduleService.getScheduleDetail(id);
        return scheduleMapper.toResponse(resp);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ScheduleResponseDTO createSchedule(@Valid @RequestBody CreateScheduleRequestDTO request) {
        ScheduleEntity entity = scheduleMapper.toEntity(request);
        ScheduleEntity resp = scheduleService.createSchedule(entity);
        return scheduleMapper.toResponse(resp);
    }

    @PatchMapping("/{id}")
    public ScheduleResponseDTO updateSchedule(@PathVariable Long id, @RequestBody Map<String, Object> patch) {
        ScheduleEntity resp = scheduleService.updateSchedule(id, patch);
        return scheduleMapper.toResponse(resp);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSchedule(@PathVariable Long id) {
        scheduleService.deleteSchedule(id);
    }
}
