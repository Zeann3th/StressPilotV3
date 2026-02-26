package dev.zeann3th.stresspilot.ui.restful.dtos.flow;

import lombok.Data;

@Data
public class FlowStepResponseDTO {
    private String id;
    private String type;
    private Long endpointId;
    private String preProcessor;
    private String postProcessor;
    private String nextIfTrue;
    private String nextIfFalse;
    private String condition;
}
