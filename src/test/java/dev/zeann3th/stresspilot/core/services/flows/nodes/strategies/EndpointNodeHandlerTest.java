package dev.zeann3th.stresspilot.core.services.flows.nodes.strategies;

import dev.zeann3th.stresspilot.core.domain.commands.endpoint.ExecuteEndpointResponse;
import dev.zeann3th.stresspilot.core.domain.entities.EndpointEntity;
import dev.zeann3th.stresspilot.core.domain.entities.FlowStepEntity;
import dev.zeann3th.stresspilot.core.domain.entities.RunEntity;
import dev.zeann3th.stresspilot.core.domain.enums.EndpointType;
import dev.zeann3th.stresspilot.core.domain.enums.FlowStepType;
import dev.zeann3th.stresspilot.core.services.RequestLogService;
import dev.zeann3th.stresspilot.core.services.configs.ConfigService;
import dev.zeann3th.stresspilot.core.services.executors.EndpointExecutor;
import dev.zeann3th.stresspilot.core.services.executors.EndpointExecutorFactory;
import dev.zeann3th.stresspilot.core.services.executors.context.ExecutionContext;
import dev.zeann3th.stresspilot.core.services.flows.FlowExecutionContext;
import dev.zeann3th.stresspilot.core.services.flows.strategies.distributed.DistributedEventPublisher;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EndpointNodeHandlerTest {

    @Test
    void jsEndpointExecutionDoesNotQueueRequestLog() {
        HandlerFixture fixture = fixtureFor(EndpointType.JS.name());

        fixture.handler.handle(fixture.step, Map.of(), fixture.context);

        verify(fixture.requestLogService, never()).queueLog(any());
        verify(fixture.distributedEventPublisher, never()).publishRequestLog(any());
        assertThat(fixture.context.getRequestCount()).isZero();
        assertThat(fixture.context.getFailureCount()).isZero();
    }

    @Test
    void nonJsEndpointExecutionStillQueuesRequestLog() {
        HandlerFixture fixture = fixtureFor(EndpointType.HTTP.name());

        fixture.handler.handle(fixture.step, Map.of(), fixture.context);

        verify(fixture.requestLogService).queueLog(any());
        verify(fixture.distributedEventPublisher, never()).publishRequestLog(any());
        assertThat(fixture.context.getRequestCount()).isEqualTo(1);
        assertThat(fixture.context.getFailureCount()).isZero();
    }

    @Test
    void distributedWorkerNonJsEndpointExecutionPublishesRequestLogInsteadOfQueueingLocally() {
        HandlerFixture fixture = fixtureFor(EndpointType.HTTP.name(), true);

        fixture.handler.handle(fixture.step, Map.of(), fixture.context);

        verify(fixture.requestLogService, never()).queueLog(any());
        verify(fixture.distributedEventPublisher).publishRequestLog(any());
        assertThat(fixture.context.getRequestCount()).isEqualTo(1);
        assertThat(fixture.context.getFailureCount()).isZero();
    }

    private HandlerFixture fixtureFor(String endpointType) {
        return fixtureFor(endpointType, false);
    }

    private HandlerFixture fixtureFor(String endpointType, boolean distributedWorker) {
        EndpointExecutorFactory executorFactory = mock(EndpointExecutorFactory.class);
        RequestLogService requestLogService = mock(RequestLogService.class);
        DistributedEventPublisher distributedEventPublisher = mock(DistributedEventPublisher.class);
        ConfigService configService = mock(ConfigService.class);
        EndpointExecutor executor = new SuccessfulExecutor(endpointType);
        when(executorFactory.getExecutor(endpointType)).thenReturn(executor);

        EndpointNodeHandler handler = new EndpointNodeHandler(
                executorFactory,
                requestLogService,
                configService,
                distributedEventPublisher);
        EndpointEntity endpoint = EndpointEntity.builder()
                .id(11L)
                .name(endpointType + " endpoint")
                .type(endpointType)
                .build();
        FlowStepEntity step = FlowStepEntity.builder()
                .id("call")
                .type(FlowStepType.ENDPOINT.name())
                .endpoint(endpoint)
                .build();
        FlowExecutionContext context = FlowExecutionContext.builder()
                .runId("run-js-log-test")
                .run(RunEntity.builder().id("run-js-log-test").build())
                .variables(new ConcurrentHashMap<>())
                .distributedWorker(distributedWorker)
                .build();

        return new HandlerFixture(handler, requestLogService, distributedEventPublisher, step, context);
    }

    private record HandlerFixture(
            EndpointNodeHandler handler,
            RequestLogService requestLogService,
            DistributedEventPublisher distributedEventPublisher,
            FlowStepEntity step,
            FlowExecutionContext context) {
    }

    private record SuccessfulExecutor(String type) implements EndpointExecutor {
        @Override
        public String getType() {
            return type;
        }

        @Override
        public ExecuteEndpointResponse execute(
                EndpointEntity endpoint,
                Map<String, Object> environment,
                ExecutionContext context) {
            return ExecuteEndpointResponse.builder()
                    .statusCode(200)
                    .success(true)
                    .responseTimeMs(1L)
                    .rawResponse("ok")
                    .build();
        }
    }
}
