package dev.zeann3th.stresspilot.ui.restful.dtos.report;

import dev.zeann3th.stresspilot.ui.restful.dtos.BaseDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class CustomReportSheetResponseDTO extends BaseDTO {
    private Long id;
    private String name;
    private int displayOrder;
    private List<CustomReportElementResponseDTO> elements;
}
