package dev.zeann3th.stresspilot.core.ports.store;

import dev.zeann3th.stresspilot.core.domain.entities.RequestLogEntity;

import java.util.List;

public interface RequestLogStore {
    RequestLogEntity save(RequestLogEntity requestLogEntity);

    List<RequestLogEntity> saveAll(Iterable<RequestLogEntity> entities);

    List<RequestLogEntity> findAllByRunId(Long runId);
}
