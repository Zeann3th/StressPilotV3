package dev.zeann3th.stresspilot.core.services.jobs;

import dev.zeann3th.stresspilot.core.domain.entities.ScheduleEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Map;

public interface ScheduleService {
    Page<ScheduleEntity> getListSchedule(Pageable pageable);

    ScheduleEntity getScheduleDetail(Long id);

    ScheduleEntity createSchedule(ScheduleEntity schedule);

    ScheduleEntity updateSchedule(Long id, Map<String, Object> patch);

    void deleteSchedule(Long id);
}
