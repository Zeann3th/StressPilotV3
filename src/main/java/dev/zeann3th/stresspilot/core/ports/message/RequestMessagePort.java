package dev.zeann3th.stresspilot.core.ports.message;

import dev.zeann3th.stresspilot.core.domain.entities.RequestLogEntity;

public interface RequestMessagePort {

    void write(RequestLogEntity log);

    void flush();
}
