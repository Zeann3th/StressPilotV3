package dev.zeann3th.stresspilot.ui.restful.dtos.report;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CustomReportElementRequestDTO {
    private String name;
    @NotNull
    private String type;
    private String config;
    private Integer displayOrder;
}
