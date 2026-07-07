package dev.zeann3th.stresspilot.ui.mcp;

import dev.zeann3th.stresspilot.core.domain.commands.function.CreateFunctionCommand;
import dev.zeann3th.stresspilot.core.domain.commands.function.UpdateFunctionCommand;
import dev.zeann3th.stresspilot.core.services.functions.FunctionService;
import dev.zeann3th.stresspilot.ui.restful.dtos.function.FunctionResponseDTO;
import dev.zeann3th.stresspilot.ui.restful.mappers.FunctionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class FunctionMcpTools {

    private final FunctionService functionService;
    private final FunctionMapper functionMapper;

    @McpTool(description = "List custom functions/processors", generateOutputSchema = true)
    public Page<FunctionResponseDTO> listFunctions(
            @McpToolParam(description = "Optional name filter") String name) {
        return functionService.getListFunction(name, PageRequest.of(0, 100))
                .map(functionMapper::toResponse);
    }

    @McpTool(description = "List all custom functions/processors without pagination", generateOutputSchema = true)
    public List<FunctionResponseDTO> listAllFunctions() {
        return functionService.getAllFunctions().stream()
                .map(functionMapper::toResponse)
                .toList();
    }

    @McpTool(description = "Get detailed information about a function", generateOutputSchema = true)
    public FunctionResponseDTO getFunctionDetail(
            @McpToolParam(description = "Function ID") Long functionId) {
        return functionMapper.toResponse(functionService.getFunctionDetail(functionId));
    }

    @McpTool(description = "Create a new script function. Example JSON: { \"name\": \"calculateHash\", \"body\": \"function calculateHash(input) { ... }\", \"isActive\": true }", generateOutputSchema = true)
    public FunctionResponseDTO createFunction(
            @McpToolParam(description = "Creation command") CreateFunctionCommand cmd) {
        return functionMapper.toResponse(functionService.createFunction(cmd));
    }

    @McpTool(description = "Update an existing function", generateOutputSchema = true)
    public FunctionResponseDTO updateFunction(
            @McpToolParam(description = "Function ID") Long functionId,
            @McpToolParam(description = "Update command") UpdateFunctionCommand cmd) {
        return functionMapper.toResponse(functionService.updateFunction(functionId, cmd));
    }

    @McpTool(description = "Delete a function")
    public void deleteFunction(
            @McpToolParam(description = "Function ID") Long functionId) {
        functionService.deleteFunction(functionId);
    }
}
