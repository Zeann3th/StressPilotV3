package dev.zeann3th.stresspilot.ui.restful;

import dev.zeann3th.stresspilot.core.services.RunService;
import dev.zeann3th.stresspilot.ui.restful.dtos.runs.RunResponseDTO;
import dev.zeann3th.stresspilot.ui.restful.mappers.RunMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/runs")
@RequiredArgsConstructor
public class RunController {
    private final RunService runService;
    private final RunMapper runMapper;

    @GetMapping
    public ResponseEntity<List<RunResponseDTO>> getAllRuns(@RequestParam(value = "flowId", required = false) Long flowId) {
        var entities = runService.getAllRuns(flowId);
        return ResponseEntity.ok(runMapper.toDTOList(entities));
    }

    @GetMapping("/{runId}")
    public ResponseEntity<RunResponseDTO> getRunDetail(@PathVariable Long runId) {
        var entity = runService.getRunDetail(runId);
        return ResponseEntity.ok(runMapper.toDTO(entity));
    }

    // exportRun and getLastRun can be added when implemented in RunService
}
