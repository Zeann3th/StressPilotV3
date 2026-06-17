package dev.zeann3th.stresspilot.infrastructure.adapters.store.report;

import dev.zeann3th.stresspilot.core.domain.entities.CustomReportSheetEntity;
import dev.zeann3th.stresspilot.core.ports.store.CustomReportSheetStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class CustomReportSheetStoreAdapter implements CustomReportSheetStore {
    private final CustomReportSheetJpaRepository jpaRepository;

    @Override
    public List<CustomReportSheetEntity> findAll() {
        return jpaRepository.findAllByOrderByDisplayOrderAsc();
    }

    @Override
    public Optional<CustomReportSheetEntity> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public CustomReportSheetEntity save(CustomReportSheetEntity entity) {
        return jpaRepository.save(entity);
    }

    @Override
    public void deleteById(Long id) {
        jpaRepository.deleteById(id);
    }

    @Override
    public boolean existsById(Long id) {
        return jpaRepository.existsById(id);
    }
}
