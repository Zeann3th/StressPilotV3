package dev.zeann3th.stresspilot.core.ports.message;

import dev.zeann3th.stresspilot.core.domain.entities.MetricScrapeEventEntity;

public interface MetricMessagePort {

    void write(MetricScrapeEventEntity event);

    void flush();
}
