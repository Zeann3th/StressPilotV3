package dev.zeann3th.stresspilot.core.ports.store;

import dev.zeann3th.stresspilot.core.domain.commands.run.RunReport;
import dev.zeann3th.stresspilot.core.domain.entities.RequestLogEntity;
import dev.zeann3th.stresspilot.core.domain.entities.RunEntity;

import java.util.List;
import java.util.function.Consumer;

public interface RequestLogStore {
    RequestLogEntity save(RequestLogEntity entity);
    List<RequestLogEntity> saveAll(Iterable<RequestLogEntity> entities);
    RunReport calculateRunReport(String runId, RunEntity run);

    void streamLogsByRunId(String runId, Consumer<RequestLogEntity> consumer);
}