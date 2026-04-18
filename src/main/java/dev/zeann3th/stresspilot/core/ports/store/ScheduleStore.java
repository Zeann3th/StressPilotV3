package dev.zeann3th.stresspilot.core.ports.store;

import dev.zeann3th.stresspilot.core.domain.entities.ScheduleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ScheduleStore extends JpaRepository<ScheduleEntity, Long> {
    List<ScheduleEntity> findAllByEnabledTrue();
}
