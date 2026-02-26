package dev.zeann3th.stresspilot.core.domain.commands.flow;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/** Command to start a flow run with concurrency + credentials config. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RunFlowCommand {
    /** Number of concurrent threads. */
    private int threads;
    /** Total run wall-clock duration in seconds. */
    private int totalDuration;
    /** Ramp-up time in seconds before all threads are active. */
    private int rampUpDuration;
    /** Extra variables injected into every thread's environment map. */
    private Map<String, Object> variables;
    /**
     * Per-thread credential sets. Thread i picks credentials[i % credentials.size()].
     * Useful for round-robin user simulation.
     */
    private List<Map<String, Object>> credentials;
}
