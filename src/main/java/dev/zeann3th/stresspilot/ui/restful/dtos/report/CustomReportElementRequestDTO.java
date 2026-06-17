package dev.zeann3th.stresspilot.ui.restful.dtos.report;

import lombok.Data;

@Data
public class CustomReportElementRequestDTO {
    private String name;
    private String type;
    private String config;
    private Integer displayOrder;
}
