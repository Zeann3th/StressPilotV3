package dev.zeann3th.stresspilot.core.services;

import dev.zeann3th.stresspilot.core.domain.entities.RequestLogEntity;

public interface RequestLogService {
    void queueLog(RequestLogEntity logEntity);

    void ensureFlushed();
}
