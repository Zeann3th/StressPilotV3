package dev.zeann3th.stresspilot.infrastructure.adapters.store.logs;

import dev.zeann3th.stresspilot.core.domain.entities.RequestLogEntity;
import dev.zeann3th.stresspilot.core.ports.store.RequestLogStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class RequestLogStoreAdapter implements RequestLogStore {
    private final RequestLogJpaRepository requestLogJpaRepository;

    @Override
    public RequestLogEntity save(RequestLogEntity requestLogEntity) {
        return requestLogJpaRepository.save(requestLogEntity);
    }

    @Override
    public List<RequestLogEntity> saveAll(Iterable<RequestLogEntity> entities) {
        return requestLogJpaRepository.saveAll(entities);
    }

    @Override
    public List<RequestLogEntity> findAllByRunId(Long runId) {
        return requestLogJpaRepository.findAllByRunId(runId);
    }
}
