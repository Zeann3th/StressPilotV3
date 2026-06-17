package dev.zeann3th.stresspilot.ui.restful.dtos.report;

import dev.zeann3th.stresspilot.ui.restful.dtos.BaseDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class CustomReportElementResponseDTO extends BaseDTO {
    private Long id;
    private Long sheetId;
    private String name;
    private String type;
    private String config;
    private int displayOrder;
}
