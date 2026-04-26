package dev.zeann3th.stresspilot.core.services.flows;

import dev.zeann3th.stresspilot.core.domain.enums.RunStatus;
import dev.zeann3th.stresspilot.core.ports.store.RunStore;
import dev.zeann3th.stresspilot.core.services.RequestLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class FlowAsyncRunner {

    private final FlowExecutorFactory flowExecutorFactory;
    private final RunStore runStore;
    private final RequestLogService requestLogService;

    @Async
    public void run(FlowExecutionContext context) {
        String runId = context.getRunId();
        String finalStatus = RunStatus.ABORTED.name();
        try {
            finalStatus = flowExecutorFactory.getStrategy(context.getFlowType()).execute(context);
        } catch (Exception e) {
            log.error("Error executing flow run {}", runId, e);
        } finally {
            int updated = runStore.finalizeRun(runId, finalStatus, LocalDateTime.now());
            if (updated == 0) {
                log.info("Run {} already finalized externally (likely ABORTED)", runId);
            }
            requestLogService.ensureFlushed();
            log.info("Run {} finished: status={}", runId, finalStatus);
        }
    }
}
