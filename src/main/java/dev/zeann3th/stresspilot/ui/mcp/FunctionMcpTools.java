package dev.zeann3th.stresspilot.ui.mcp;

import dev.zeann3th.stresspilot.core.domain.commands.function.CreateFunctionCommand;
import dev.zeann3th.stresspilot.core.domain.commands.function.UpdateFunctionCommand;
import dev.zeann3th.stresspilot.core.domain.entities.FunctionEntity;
import dev.zeann3th.stresspilot.core.services.functions.FunctionService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FunctionMcpTools {

    private final FunctionService functionService;

    @McpTool(description = "List custom functions/processors")
    public Page<FunctionEntity> listFunctions(
            @McpToolParam(description = "Optional name filter") String name) {
        return functionService.getListFunction(name, PageRequest.of(0, 100));
    }

    @McpTool(description = "Get detailed information about a function")
    public FunctionEntity getFunctionDetail(
            @McpToolParam(description = "Function ID") Long functionId) {
        return functionService.getFunctionDetail(functionId);
    }

    @McpTool(description = "Create a new script function. Example JSON: { \"name\": \"calculateHash\", \"body\": \"function calculateHash(input) { ... }\", \"isActive\": true }")
    public FunctionEntity createFunction(
            @McpToolParam(description = "Creation command") CreateFunctionCommand cmd) {
        return functionService.createFunction(cmd);
    }

    @McpTool(description = "Update an existing function")
    public FunctionEntity updateFunction(
            @McpToolParam(description = "Function ID") Long functionId,
            @McpToolParam(description = "Update command") UpdateFunctionCommand cmd) {
        return functionService.updateFunction(functionId, cmd);
    }

    @McpTool(description = "Delete a function")
    public void deleteFunction(
            @McpToolParam(description = "Function ID") Long functionId) {
        functionService.deleteFunction(functionId);
    }
}
