package dev.zeann3th.stresspilot.infrastructure.adapters.store.report;

import dev.zeann3th.stresspilot.core.domain.entities.CustomReportSheetEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CustomReportSheetJpaRepository extends JpaRepository<CustomReportSheetEntity, Long> {
    List<CustomReportSheetEntity> findAllByOrderByDisplayOrderAsc();
}
