package dev.zeann3th.stresspilot.ui.restful.dtos.flow;

import lombok.Data;
import java.util.Map;

@Data
public class FlowStepRequestDTO {
    private String id;
    private String type;
    private Long endpointId;
    private Map<String, Object> preProcessor;
    private Map<String, Object> postProcessor;
    private String nextIfTrue;
    private String nextIfFalse;
    private String condition;
}
