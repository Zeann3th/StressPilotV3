package dev.zeann3th.stresspilot.core.services.jobs;

import dev.zeann3th.stresspilot.core.domain.constants.Constants;
import dev.zeann3th.stresspilot.core.domain.entities.ScheduleEntity;
import dev.zeann3th.stresspilot.core.domain.enums.ErrorCode;
import dev.zeann3th.stresspilot.core.domain.exception.CommandExceptionBuilder;
import dev.zeann3th.stresspilot.core.ports.store.FlowStore;
import dev.zeann3th.stresspilot.core.ports.store.ScheduleStore;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleServiceImpl implements ScheduleService {

    private static final String FLOW_RUN_GROUP = "flowRunGroup";
    private final Scheduler scheduler;
    private final ScheduleStore scheduleStore;
    private final FlowStore flowStore;
    private final JsonMapper jsonMapper;

    @PostConstruct
    public void init() {
        log.info("Initializing scheduled jobs from database...");
        List<ScheduleEntity> enabledSchedules = scheduleStore.findAllByEnabledTrue();
        for (ScheduleEntity schedule : enabledSchedules) {
            try {
                scheduleJob(schedule);
            } catch (SchedulerException e) {
                log.error("Failed to schedule job for schedule {}: {}", schedule.getId(), e.getMessage());
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ScheduleEntity> getListSchedule(Pageable pageable) {
        return scheduleStore.findAll(pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public ScheduleEntity getScheduleDetail(Long id) {
        return scheduleStore.findById(id)
                .orElseThrow(() -> CommandExceptionBuilder.exception(ErrorCode.ER0003));
    }

    @Override
    @Transactional
    public ScheduleEntity createSchedule(ScheduleEntity schedule) {
        if (schedule.getFlow() == null || schedule.getFlow().getId() == null) {
            throw CommandExceptionBuilder.exception(ErrorCode.ER0003);
        }
        var flow = flowStore.findById(schedule.getFlow().getId())
                .orElseThrow(() -> CommandExceptionBuilder.exception(ErrorCode.ER0003));
        schedule.setFlow(flow);
        
        ScheduleEntity saved = scheduleStore.save(schedule);
        if (Boolean.TRUE.equals(saved.getEnabled())) {
            try {
                scheduleJob(saved);
            } catch (SchedulerException e) {
                throw CommandExceptionBuilder.exception(ErrorCode.ER0026, Map.of(Constants.REASON, e.getMessage()));
            }
        }
        return saved;
    }

    @Override
    @Transactional
    public ScheduleEntity updateSchedule(Long id, Map<String, Object> patch) {
        ScheduleEntity entity = scheduleStore.findById(id)
                .orElseThrow(() -> CommandExceptionBuilder.exception(ErrorCode.ER0003));
        
        Map<String, Object> safe = patch.entrySet().stream()
                .filter(e -> !Set.of("id", "flow", "createdAt", "updatedAt").contains(e.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        
        try {
            jsonMapper.updateValue(entity, safe);
            ScheduleEntity saved = scheduleStore.save(entity);
            
            if (Boolean.TRUE.equals(saved.getEnabled())) {
                scheduleJob(saved);
            } else {
                unscheduleJob(saved.getId());
            }
            return saved;
        } catch (Exception ex) {
            throw CommandExceptionBuilder.exception(ErrorCode.ER0019,
                    Map.of(Constants.REASON, ex.getMessage()));
        }
    }

    @Override
    @Transactional
    public void deleteSchedule(Long id) {
        if (!scheduleStore.existsById(id)) {
            throw CommandExceptionBuilder.exception(ErrorCode.ER0003);
        }
        scheduleStore.deleteById(id);
        try {
            unscheduleJob(id);
        } catch (SchedulerException e) {
            log.error("Failed to unschedule job {}", id, e);
        }
    }

    private void scheduleJob(ScheduleEntity schedule) throws SchedulerException {
        JobDetail jobDetail = JobBuilder.newJob(FlowRunJob.class)
                .withIdentity("flowRunJob_" + schedule.getId(), FLOW_RUN_GROUP)
                .usingJobData("scheduleId", schedule.getId())
                .storeDurably()
                .build();

        CronTrigger trigger = TriggerBuilder.newTrigger()
                .withIdentity("flowRunTrigger_" + schedule.getId(), FLOW_RUN_GROUP)
                .withSchedule(CronScheduleBuilder.cronSchedule(schedule.getQuartzExpr()))
                .build();

        if (scheduler.checkExists(jobDetail.getKey())) {
            scheduler.deleteJob(jobDetail.getKey());
        }

        scheduler.scheduleJob(jobDetail, trigger);
        log.info("Scheduled flow job {} with expression: {}", schedule.getId(), schedule.getQuartzExpr());
    }

    private void unscheduleJob(Long scheduleId) throws SchedulerException {
        JobKey jobKey = new JobKey("flowRunJob_" + scheduleId, FLOW_RUN_GROUP);
        if (scheduler.checkExists(jobKey)) {
            scheduler.deleteJob(jobKey);
            log.info("Unscheduled flow job {}", scheduleId);
        }
    }
}
