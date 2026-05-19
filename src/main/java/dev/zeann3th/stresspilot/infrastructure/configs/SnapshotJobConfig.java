package dev.zeann3th.stresspilot.infrastructure.configs;

import dev.zeann3th.stresspilot.infrastructure.adapters.jobs.RunSnapshotJob;
import org.quartz.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SnapshotJobConfig {

    @Value("${application.tasks.run-snapshot.cron:0 0/10 * * * ?}")
    private String cronExpression;

    @Bean
    public JobDetail runSnapshotJobDetail() {
        return JobBuilder.newJob(RunSnapshotJob.class)
                .withIdentity("runSnapshotJob", "systemGroup")
                .storeDurably()
                .build();
    }

    @Bean
    public Trigger runSnapshotJobTrigger(JobDetail runSnapshotJobDetail) {
        return TriggerBuilder.newTrigger()
                .forJob(runSnapshotJobDetail)
                .withIdentity("runSnapshotJobTrigger", "systemGroup")
                .withSchedule(CronScheduleBuilder.cronSchedule(cronExpression))
                .build();
    }
}
