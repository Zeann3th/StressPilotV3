package dev.zeann3th.stresspilot.core.domain.commands.flow;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/** Represents a single step in a flow configuration command. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlowStepCommand {
    /** Client-side transient ID (UUID string) used only during configure. */
    private String id;
    /** FlowStepType name: START, ENDPOINT, BRANCH. */
    private String type;
    /** Must be set for ENDPOINT steps. */
    private Long endpointId;
    /** Pre-processor config (sleep/inject/extract). */
    private Map<String, Object> preProcessor;
    /** Post-processor config (sleep/inject/extract). */
    private Map<String, Object> postProcessor;
    /** ID of the next step if true / success. */
    private String nextIfTrue;
    /** ID of the next step if false / failure. */
    private String nextIfFalse;
    /** SpEL condition string for BRANCH nodes. */
    private String condition;
}
