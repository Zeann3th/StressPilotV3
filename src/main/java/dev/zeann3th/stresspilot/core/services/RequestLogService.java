package dev.zeann3th.stresspilot.core.services;

import dev.zeann3th.stresspilot.core.domain.entities.RequestLogEntity;
import dev.zeann3th.stresspilot.core.ports.message.RequestMessagePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j(topic = "[RequestLogService]")
@Service
@RequiredArgsConstructor
public class RequestLogService {

    private final List<RequestMessagePort> writers;

    public void queueLog(RequestLogEntity logEntity) {
        writers.forEach(w -> {
            try {
                w.write(logEntity);
            } catch (Exception e) {
                log.warn("Writer {} failed on write: {}", w.getClass().getSimpleName(), e.getMessage());
            }
        });
    }

    public void ensureFlushed() {
        writers.forEach(w -> {
            try {
                w.flush();
            } catch (Exception e) {
                log.warn("Writer {} failed on flush: {}", w.getClass().getSimpleName(), e.getMessage());
            }
        });
    }
}
