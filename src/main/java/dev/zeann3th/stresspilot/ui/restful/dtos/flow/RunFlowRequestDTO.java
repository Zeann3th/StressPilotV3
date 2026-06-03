package dev.zeann3th.stresspilot.ui.restful.dtos.flow;

import jakarta.validation.constraints.Min;
import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class RunFlowRequestDTO {
    private Long environmentId;
    @Min(1)
    private int threads;
    @Min(1)
    private Integer totalDuration;
    @Min(1)
    private Integer loopCount;
    private int rampUpDuration;
    private Map<String, Object> variables;
    private List<Map<String, Object>> credentials;
}
