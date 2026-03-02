package dev.zeann3th.stresspilot.ui.restful.dtos.flow;

import dev.zeann3th.stresspilot.ui.restful.dtos.BaseDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class FlowResponseDTO extends BaseDTO {
    private Long id;
    private String name;
    private String description;
    private Long projectId;
    private List<FlowStepResponseDTO> steps;
}
