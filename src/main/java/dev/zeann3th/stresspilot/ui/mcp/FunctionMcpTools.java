package dev.zeann3th.stresspilot.ui.mcp;

import dev.zeann3th.stresspilot.core.domain.commands.function.CreateFunctionCommand;
import dev.zeann3th.stresspilot.core.domain.commands.function.UpdateFunctionCommand;
import dev.zeann3th.stresspilot.core.domain.entities.FunctionEntity;
import dev.zeann3th.stresspilot.core.services.functions.FunctionService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FunctionMcpTools {

    private final FunctionService functionService;

    @Tool(description = "List custom functions/processors")
    public Page<FunctionEntity> listFunctions(
            @ToolParam(description = "Optional name filter") String name) {
        return functionService.getListFunction(name, PageRequest.of(0, 100));
    }

    @Tool(description = "Get detailed information about a function")
    public FunctionEntity getFunctionDetail(
            @ToolParam(description = "Function ID") Long functionId) {
        return functionService.getFunctionDetail(functionId);
    }

    @Tool(description = "Create a new script function. Example JSON: { \"name\": \"calculateHash\", \"body\": \"function calculateHash(input) { ... }\", \"isActive\": true }")
    public FunctionEntity createFunction(
            @ToolParam(description = "Creation command") CreateFunctionCommand cmd) {
        return functionService.createFunction(cmd);
    }

    @Tool(description = "Update an existing function")
    public FunctionEntity updateFunction(
            @ToolParam(description = "Function ID") Long functionId,
            @ToolParam(description = "Update command") UpdateFunctionCommand cmd) {
        return functionService.updateFunction(functionId, cmd);
    }

    @Tool(description = "Delete a function")
    public void deleteFunction(
            @ToolParam(description = "Function ID") Long functionId) {
        functionService.deleteFunction(functionId);
    }
}
