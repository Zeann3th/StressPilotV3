package dev.zeann3th.stresspilot.ui.restful.controllers;

import dev.zeann3th.stresspilot.core.domain.entities.EnvironmentVariableEntity;
import dev.zeann3th.stresspilot.core.services.environments.EnvironmentService;
import dev.zeann3th.stresspilot.ui.restful.dtos.environment.EnvironmentVariableResponseDTO;
import dev.zeann3th.stresspilot.ui.restful.dtos.environment.UpdateEnvironmentVariablesRequestDTO;
import dev.zeann3th.stresspilot.ui.restful.exception.ResponseWrapper;
import dev.zeann3th.stresspilot.ui.restful.mappers.EnvironmentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/environments")
@RequiredArgsConstructor
@ResponseWrapper
public class EnvironmentController {

    private final EnvironmentService environmentService;
    private final EnvironmentMapper environmentMapper;

    @GetMapping("/{environmentId}/variables")
    public List<EnvironmentVariableResponseDTO> getEnvironmentVariables(
            @PathVariable("environmentId") Long environmentId) {
        List<EnvironmentVariableEntity> variables = environmentService.getEnvironmentVariables(environmentId);
        return variables.stream().map(environmentMapper::toResponse).toList();
    }

    @PatchMapping("/{environmentId}/variables")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateEnvironmentVariables(@PathVariable("environmentId") Long environmentId,
            @RequestBody UpdateEnvironmentVariablesRequestDTO request) {
        environmentService.updateEnvironmentVariables(environmentId, environmentMapper.toUpdateCommand(request));
    }
}
