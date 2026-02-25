package dev.zeann3th.stresspilot.ui.restful;

import dev.zeann3th.stresspilot.core.services.EnvironmentService;
import dev.zeann3th.stresspilot.ui.restful.dtos.environments.EnvironmentVariableDTO;
import dev.zeann3th.stresspilot.ui.restful.dtos.environments.UpdateEnvironmentRequestDTO;
import dev.zeann3th.stresspilot.ui.restful.mappers.EnvironmentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/environments")
@RequiredArgsConstructor
public class EnvironmentController {
    private final EnvironmentService environmentService;
    private final EnvironmentMapper environmentMapper;

    @GetMapping("/{environmentId}/variables")
    public ResponseEntity<List<EnvironmentVariableDTO>> getEnvironmentVariables(@PathVariable("environmentId") Long environmentId) {
        var entities = environmentService.getEnvironmentVariables(environmentId);
        return ResponseEntity.ok(environmentMapper.toDTOList(entities));
    }

    @PatchMapping("/{environmentId}/variables")
    public ResponseEntity<Void> updateEnvironmentVariables(@PathVariable("environmentId") Long environmentId,
                                                           @RequestBody UpdateEnvironmentRequestDTO updateEnvironmentRequestDTO) {
        var command = environmentMapper.toUpdateCommand(environmentId, updateEnvironmentRequestDTO);
        environmentService.updateEnvironmentVariables(command);
        return ResponseEntity.noContent().build();
    }
}
