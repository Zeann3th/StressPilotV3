package dev.zeann3th.stresspilot.ui.restful.dtos.flow;

import lombok.Data;

import java.util.List;

@Data
public class FlowResponseDTO {
    private Long id;
    private String name;
    private String description;
    private Long projectId;
    private List<FlowStepResponseDTO> steps;
}
