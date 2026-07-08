package dev.zeann3th.stresspilot.ui.mcp;

import org.junit.jupiter.api.Test;
import org.springframework.ai.mcp.annotation.McpTool;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class McpToolResponseTypeTest {

    private static final List<Class<?>> MCP_TOOL_CLASSES = List.of(
            ConfigMcpTools.class,
            EndpointMcpTools.class,
            EnvironmentMcpTools.class,
            FlowMcpTools.class,
            FunctionMcpTools.class,
            ProjectMcpTools.class,
            RunMcpTools.class,
            ScheduleMcpTools.class
    );

    @Test
    void mcpToolsDoNotExposeJpaEntitiesInResponses() {
        List<String> entityResponses = MCP_TOOL_CLASSES.stream()
                .flatMap(toolClass -> Arrays.stream(toolClass.getDeclaredMethods()))
                .filter(method -> method.isAnnotationPresent(McpTool.class))
                .filter(method -> containsDomainEntity(method.getGenericReturnType()))
                .map(this::methodName)
                .toList();

        assertThat(entityResponses).isEmpty();
    }

    private boolean containsDomainEntity(Type type) {
        if (type instanceof Class<?> clazz) {
            return clazz.getPackageName().startsWith("dev.zeann3th.stresspilot.core.domain.entities");
        }
        if (type instanceof ParameterizedType parameterizedType) {
            if (containsDomainEntity(parameterizedType.getRawType())) {
                return true;
            }
            return Arrays.stream(parameterizedType.getActualTypeArguments())
                    .anyMatch(this::containsDomainEntity);
        }
        return false;
    }

    private String methodName(Method method) {
        return method.getDeclaringClass().getSimpleName() + "." + method.getName();
    }
}
