package dev.zeann3th.stresspilot.core.ports.message;

import dev.zeann3th.stresspilot.core.domain.entities.RequestLogEntity;

import java.util.List;

public interface RequestMessagePort {

    void write(RequestLogEntity log);

    void writeAll(List<RequestLogEntity> logs);

    void flush();
}
