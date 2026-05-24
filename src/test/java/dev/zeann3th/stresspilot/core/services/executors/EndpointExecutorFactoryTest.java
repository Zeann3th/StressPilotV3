package dev.zeann3th.stresspilot.core.services.executors;

import dev.zeann3th.stresspilot.core.domain.commands.endpoint.ExecuteEndpointResponse;
import dev.zeann3th.stresspilot.core.domain.entities.EndpointEntity;
import dev.zeann3th.stresspilot.core.services.executors.context.ExecutionContext;
import dev.zeann3th.stresspilot.core.services.executors.strategies.JsEndpointExecutor;
import dev.zeann3th.stresspilot.core.services.functions.FunctionService;
import org.junit.jupiter.api.Test;
import org.pf4j.PluginManager;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EndpointExecutorFactoryTest {

    @Test
    void internalExecutorWinsWhenPluginRegistersSameType() {
        PluginManager pluginManager = mock(PluginManager.class);
        EndpointExecutor maliciousPluginJs = new StubExecutor("JS");
        when(pluginManager.getExtensions(EndpointExecutor.class)).thenReturn(List.of(maliciousPluginJs));

        JsEndpointExecutor internalJs = new JsEndpointExecutor(mock(FunctionService.class));
        EndpointExecutorFactory factory = new EndpointExecutorFactory(List.of(internalJs), pluginManager);

        assertThat(factory.getExecutor("JS")).isSameAs(internalJs);
    }

    @Test
    void listTypesKeepsInternalTypeOnlyOnceWhenPluginDuplicatesIt() {
        PluginManager pluginManager = mock(PluginManager.class);
        when(pluginManager.getExtensions(EndpointExecutor.class))
                .thenReturn(List.of(new StubExecutor("JS"), new StubExecutor("SMTP")));

        JsEndpointExecutor internalJs = new JsEndpointExecutor(mock(FunctionService.class));
        EndpointExecutorFactory factory = new EndpointExecutorFactory(List.of(internalJs), pluginManager);

        List<String> types = factory.listTypes();
        assertThat(types).containsExactly("JS", "SMTP");
        assertThatThrownBy(() -> types.add("HTTP"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private record StubExecutor(String type) implements EndpointExecutor {
        @Override
        public String getType() {
            return type;
        }

        @Override
        public ExecuteEndpointResponse execute(
                EndpointEntity endpoint,
                Map<String, Object> environment,
                ExecutionContext context) {
            return ExecuteEndpointResponse.builder().success(false).build();
        }
    }
}
