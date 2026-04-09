package dev.zeann3th.stresspilot.ui.restful;

import dev.zeann3th.stresspilot.core.domain.entities.FunctionEntity;
import dev.zeann3th.stresspilot.core.services.functions.FunctionService;
import dev.zeann3th.stresspilot.ui.restful.dtos.function.CreateFunctionRequestDTO;
import dev.zeann3th.stresspilot.ui.restful.dtos.function.FunctionResponseDTO;
import dev.zeann3th.stresspilot.ui.restful.dtos.function.UpdateFunctionRequestDTO;
import dev.zeann3th.stresspilot.ui.restful.exception.ResponseWrapper;
import dev.zeann3th.stresspilot.ui.restful.mappers.FunctionMapper;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/functions")
@RequiredArgsConstructor
@ResponseWrapper
@Tag(name = "Functions", description = "API for managing functions/processors")
public class FunctionController {

    private final FunctionService functionService;
    private final FunctionMapper functionMapper;

    @GetMapping
    public Page<FunctionResponseDTO> getListFunction(
            @RequestParam(value = "name", required = false) String name,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<FunctionEntity> resp = functionService.getListFunction(name, pageable);
        return resp.map(functionMapper::toResponse);
    }

    @GetMapping("/{functionId}")
    public FunctionResponseDTO getFunctionDetail(@PathVariable Long functionId) {
        FunctionEntity resp = functionService.getFunctionDetail(functionId);
        return functionMapper.toResponse(resp);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FunctionResponseDTO createFunction(@Valid @RequestBody CreateFunctionRequestDTO request) {
        var command = functionMapper.toCreateCommand(request);
        FunctionEntity resp = functionService.createFunction(command);
        return functionMapper.toResponse(resp);
    }

    @PutMapping("/{functionId}")
    public FunctionResponseDTO updateFunction(@PathVariable Long functionId, @Valid @RequestBody UpdateFunctionRequestDTO request) {
        var command = functionMapper.toUpdateCommand(request);
        FunctionEntity resp = functionService.updateFunction(functionId, command);
        return functionMapper.toResponse(resp);
    }

    @DeleteMapping("/{functionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteFunction(@PathVariable Long functionId) {
        functionService.deleteFunction(functionId);
    }
}
