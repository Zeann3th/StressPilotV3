package dev.zeann3th.stresspilot.ui.restful.dtos.flow;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class FlowEndpointResponseDTO {
    private Long id;
    private String name;
}
