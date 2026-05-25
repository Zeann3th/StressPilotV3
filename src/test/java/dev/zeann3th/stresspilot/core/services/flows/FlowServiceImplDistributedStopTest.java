package dev.zeann3th.stresspilot.core.services.flows;

import dev.zeann3th.stresspilot.core.domain.events.InterruptRunEvent;
import dev.zeann3th.stresspilot.core.ports.store.EndpointStore;
import dev.zeann3th.stresspilot.core.ports.store.EnvironmentVariableStore;
import dev.zeann3th.stresspilot.core.ports.store.FlowStepStore;
import dev.zeann3th.stresspilot.core.ports.store.FlowStore;
import dev.zeann3th.stresspilot.core.ports.store.ProjectStore;
import dev.zeann3th.stresspilot.core.ports.store.RunStore;
import dev.zeann3th.stresspilot.core.services.ActiveRunRegistry;
import dev.zeann3th.stresspilot.core.services.flows.strategies.distributed.DistributedEventPublisher;
import dev.zeann3th.stresspilot.core.utils.SnowflakeId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FlowServiceImplDistributedStopTest {
    @Test
    @SuppressWarnings("unchecked")
    void handleInterruptRunEventInterruptsLocalRunAndPublishesDistributedStopWhenAvailable() {
        ActiveRunRegistry activeRunRegistry = mock(ActiveRunRegistry.class);
        DistributedEventPublisher eventPublisher = mock(DistributedEventPublisher.class);
        ObjectProvider<DistributedEventPublisher> publisherProvider = mock(ObjectProvider.class);
        when(publisherProvider.getIfAvailable()).thenReturn(eventPublisher);
        when(eventPublisher.isAvailable()).thenReturn(true);
        FlowServiceImpl flowService = flowService(activeRunRegistry, publisherProvider);

        flowService.handleInterruptRunEvent(new InterruptRunEvent("run-1"));

        verify(activeRunRegistry).interruptRun("run-1");
        verify(eventPublisher).publishStop("run-1");
    }

    @Test
    @SuppressWarnings("unchecked")
    void handleInterruptRunEventInterruptsLocalRunAndSkipsDistributedStopWhenUnavailable() {
        ActiveRunRegistry activeRunRegistry = mock(ActiveRunRegistry.class);
        DistributedEventPublisher eventPublisher = mock(DistributedEventPublisher.class);
        ObjectProvider<DistributedEventPublisher> publisherProvider = mock(ObjectProvider.class);
        when(publisherProvider.getIfAvailable()).thenReturn(eventPublisher);
        when(eventPublisher.isAvailable()).thenReturn(false);
        FlowServiceImpl flowService = flowService(activeRunRegistry, publisherProvider);

        flowService.handleInterruptRunEvent(new InterruptRunEvent("run-1"));

        verify(activeRunRegistry).interruptRun("run-1");
        verify(eventPublisher, never()).publishStop("run-1");
    }

    @Test
    @SuppressWarnings("unchecked")
    void handleInterruptRunEventKeepsLocalInterruptWhenDistributedStopPublishFails() {
        ActiveRunRegistry activeRunRegistry = mock(ActiveRunRegistry.class);
        DistributedEventPublisher eventPublisher = mock(DistributedEventPublisher.class);
        ObjectProvider<DistributedEventPublisher> publisherProvider = mock(ObjectProvider.class);
        when(publisherProvider.getIfAvailable()).thenReturn(eventPublisher);
        when(eventPublisher.isAvailable()).thenReturn(true);
        doThrow(new IllegalStateException("Redis down")).when(eventPublisher).publishStop("run-1");
        FlowServiceImpl flowService = flowService(activeRunRegistry, publisherProvider);

        assertDoesNotThrow(() -> flowService.handleInterruptRunEvent(new InterruptRunEvent("run-1")));

        verify(activeRunRegistry).interruptRun("run-1");
        verify(eventPublisher).publishStop("run-1");
    }

    private static FlowServiceImpl flowService(
            ActiveRunRegistry activeRunRegistry,
            ObjectProvider<DistributedEventPublisher> publisherProvider) {
        return new FlowServiceImpl(
                mock(FlowStore.class),
                mock(FlowStepStore.class),
                mock(ProjectStore.class),
                mock(EndpointStore.class),
                mock(EnvironmentVariableStore.class),
                mock(RunStore.class),
                new JsonMapper(),
                activeRunRegistry,
                mock(FlowAsyncRunner.class),
                mock(SnowflakeId.class),
                publisherProvider);
    }
}
