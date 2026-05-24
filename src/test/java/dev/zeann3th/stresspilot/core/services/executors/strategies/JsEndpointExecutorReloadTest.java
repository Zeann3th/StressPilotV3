package dev.zeann3th.stresspilot.core.services.executors.strategies;

import dev.zeann3th.stresspilot.core.domain.entities.EndpointEntity;
import dev.zeann3th.stresspilot.core.domain.entities.FunctionEntity;
import dev.zeann3th.stresspilot.core.domain.enums.EndpointType;
import dev.zeann3th.stresspilot.core.services.executors.context.BaseExecutionContext;
import dev.zeann3th.stresspilot.core.services.functions.FunctionService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JsEndpointExecutorReloadTest {

    @Test
    void reloadsUserDefinedFunctionsOnlyAfterTheyAreMarkedDirty() {
        FunctionService functionService = mock(FunctionService.class);
        when(functionService.getAllFunctions()).thenReturn(List.of(FunctionEntity.builder()
                .name("noop")
                .body("function noop() { return true; }")
                .active(true)
                .build()));
        JsEndpointExecutor executor = new JsEndpointExecutor(functionService);
        executor.init();

        EndpointEntity endpoint = EndpointEntity.builder()
                .type(EndpointType.JS.name())
                .body("true")
                .build();

        assertThat(executor.execute(endpoint, Map.of(), new BaseExecutionContext()).isSuccess()).isTrue();
        assertThat(executor.execute(endpoint, Map.of(), new BaseExecutionContext()).isSuccess()).isTrue();
        verify(functionService, times(1)).getAllFunctions();

        executor.markUserDefinedFunctionsDirty();
        assertThat(executor.execute(endpoint, Map.of(), new BaseExecutionContext()).isSuccess()).isTrue();
        verify(functionService, times(2)).getAllFunctions();

        executor.destroy();
    }
}
