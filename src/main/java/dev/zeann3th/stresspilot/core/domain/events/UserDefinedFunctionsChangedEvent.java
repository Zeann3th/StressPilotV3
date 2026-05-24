package dev.zeann3th.stresspilot.core.domain.events;

import java.time.Instant;

public record UserDefinedFunctionsChangedEvent(Instant occurredAt) {

    public static UserDefinedFunctionsChangedEvent now() {
        return new UserDefinedFunctionsChangedEvent(Instant.now());
    }
}
