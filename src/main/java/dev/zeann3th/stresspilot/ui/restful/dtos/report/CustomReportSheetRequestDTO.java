package dev.zeann3th.stresspilot.ui.restful.dtos.report;

import lombok.Data;

@Data
public class CustomReportSheetRequestDTO {
    private String name;
    private Integer displayOrder;
}
