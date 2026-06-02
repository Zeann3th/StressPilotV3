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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
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

    @Test
    void dryRunNonJsEndpointExecutionDoesNotQueueOrPublishRequestLog() {
        HandlerFixture fixture = fixtureFor(EndpointType.HTTP.name());
        fixture.context.setPersistRequestLogs(false);
        fixture.step.getEndpoint().setUrl("https://api.example.test/users/{{userId}}");
        fixture.step.getEndpoint().setHttpMethod("POST");
        fixture.step.getEndpoint().setHttpHeaders("{\"Authorization\":\"Bearer {{token}}\"}");
        fixture.step.getEndpoint().setBody("{\"name\":\"{{name}}\"}");
        fixture.context.getVariables().put("userId", 42);
        fixture.context.getVariables().put("token", "abc123");
        fixture.context.getVariables().put("name", "Ada");

        fixture.handler.handle(fixture.step, Map.of(), fixture.context);

        verify(fixture.requestLogService, never()).queueLog(any());
        verify(fixture.distributedEventPublisher, never()).publishRequestLog(any());
        assertThat(fixture.context.getRequestCount()).isEqualTo(1);
        assertThat(fixture.context.getFailureCount()).isZero();
        assertThat(fixture.context.getDryRunRequestLogs()).hasSize(1);
        assertThat(fixture.context.getDryRunRequestLogs().getFirst().getRequest())
                .contains("https://api.example.test/users/42")
                .contains("Bearer abc123")
                .contains("Ada")
                .doesNotContain("variables_snapshot")
                .doesNotContain("{{");
        assertThat(fixture.context.getDryRunRequestLogs().getFirst().getResponse()).isEqualTo("ok");
    }

    @Test
    void strictLinearConfigContinuesToNextTrueOnFailedEndpoint() {
        HandlerFixture fixture = fixtureFor(EndpointType.HTTP.name(), false, false);
        when(fixture.configService.getValue("FLOW_ENDPOINT_STRICT_LINEAR")).thenReturn(Optional.of("true"));
        fixture.handler.init();
        FlowStepEntity next = FlowStepEntity.builder()
                .id("next")
                .type(FlowStepType.ENDPOINT.name())
                .build();
        FlowStepEntity fallback = FlowStepEntity.builder()
                .id("fallback")
                .type(FlowStepType.ENDPOINT.name())
                .build();
        fixture.step.setNextIfTrue("next");
        fixture.step.setNextIfFalse("fallback");

        var result = fixture.handler.handle(fixture.step, Map.of(
                "next", next,
                "fallback", fallback
        ), fixture.context);

        assertThat(result.nextId()).isEqualTo("next");
    }

    @Test
    void requestLogFormattingDoesNotStopFlowWhenPathVariableIsMissing() {
        HandlerFixture fixture = fixtureFor(EndpointType.HTTP.name());
        fixture.step.getEndpoint().setUrl("https://api.example.test/users/:missingUserId");
        fixture.step.setNextIfTrue("next");
        FlowStepEntity next = FlowStepEntity.builder()
                .id("next")
                .type(FlowStepType.ENDPOINT.name())
                .build();

        assertThatCode(() -> fixture.handler.handle(fixture.step, Map.of("next", next), fixture.context))
                .doesNotThrowAnyException();

        verify(fixture.requestLogService).queueLog(any());
    }

    private HandlerFixture fixtureFor(String endpointType) {
        return fixtureFor(endpointType, false, true);
    }

    private HandlerFixture fixtureFor(String endpointType, boolean distributedWorker) {
        return fixtureFor(endpointType, distributedWorker, true);
    }

    private HandlerFixture fixtureFor(String endpointType, boolean distributedWorker, boolean succeeds) {
        EndpointExecutorFactory executorFactory = mock(EndpointExecutorFactory.class);
        RequestLogService requestLogService = mock(RequestLogService.class);
        DistributedEventPublisher distributedEventPublisher = mock(DistributedEventPublisher.class);
        ConfigService configService = mock(ConfigService.class);
        EndpointExecutor executor = new TestExecutor(endpointType, succeeds);
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
                .httpMethod("GET")
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

        return new HandlerFixture(handler, configService, requestLogService, distributedEventPublisher, step, context);
    }

    private record HandlerFixture(
            EndpointNodeHandler handler,
            ConfigService configService,
            RequestLogService requestLogService,
            DistributedEventPublisher distributedEventPublisher,
            FlowStepEntity step,
            FlowExecutionContext context) {
    }

    private record TestExecutor(String type, boolean succeeds) implements EndpointExecutor {
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
                    .statusCode(succeeds ? 200 : 500)
                    .success(succeeds)
                    .responseTimeMs(1L)
                    .rawResponse(succeeds ? "ok" : "failed")
                    .build();
        }
    }
}
