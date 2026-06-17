package dev.zeann3th.stresspilot.core.ports.store;

import dev.zeann3th.stresspilot.core.domain.entities.CustomReportSheetEntity;

import java.util.List;
import java.util.Optional;

public interface CustomReportSheetStore {
    List<CustomReportSheetEntity> findAll();
    Optional<CustomReportSheetEntity> findById(Long id);
    CustomReportSheetEntity save(CustomReportSheetEntity entity);
    void deleteById(Long id);
    boolean existsById(Long id);
}
