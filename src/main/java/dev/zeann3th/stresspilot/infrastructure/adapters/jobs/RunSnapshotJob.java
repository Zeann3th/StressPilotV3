package dev.zeann3th.stresspilot.infrastructure.adapters.jobs;

import dev.zeann3th.stresspilot.core.services.runs.RunService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RunSnapshotJob implements Job {

    private final RunService runService;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        log.info("Executing system run snapshot job...");
        try {
            runService.performSnapshotting();
            log.info("System run snapshot job completed successfully.");
        } catch (Exception e) {
            log.error("Failed to execute system run snapshot job: {}", e.getMessage(), e);
            throw new JobExecutionException(e);
        }
    }
}
