package dev.zeann3th.stresspilot.core.services.jobs;

import dev.zeann3th.stresspilot.core.domain.commands.flow.RunFlowCommand;
import dev.zeann3th.stresspilot.core.domain.entities.ScheduleEntity;
import dev.zeann3th.stresspilot.core.ports.store.ScheduleStore;
import dev.zeann3th.stresspilot.core.services.flows.FlowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class FlowRunJob implements Job {

    private final FlowService flowService;
    private final ScheduleStore scheduleStore;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        Long scheduleId = context.getJobDetail().getJobDataMap().getLong("scheduleId");
        ScheduleEntity schedule = scheduleStore.findById(scheduleId).orElse(null);

        if (schedule == null) {
            log.warn("Scheduled job triggered for non-existent schedule: {}", scheduleId);
            return;
        }

        if (Boolean.FALSE.equals(schedule.getEnabled())) {
            log.info("Scheduled job {} is disabled, skipping.", scheduleId);
            return;
        }

        log.info("Executing scheduled flow run for schedule: {}", scheduleId);

        try {
            RunFlowCommand cmd = RunFlowCommand.builder()
                    .threads(schedule.getThreads())
                    .totalDuration(schedule.getDuration())
                    .rampUpDuration(schedule.getRampUp())
                    .build();

            String runId = flowService.runFlow(schedule.getFlow().getId(), cmd);
            log.info("Scheduled flow run started with ID: {}", runId);
        } catch (Exception e) {
            log.error("Failed to execute scheduled flow run for schedule {}: {}", scheduleId, e.getMessage(), e);
            throw new JobExecutionException(e);
        }
    }
}
