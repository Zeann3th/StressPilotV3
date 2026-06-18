package dev.zeann3th.stresspilot.ui.restful.dtos.report;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CustomReportSheetRequestDTO {
    @NotBlank
    @Size(max = 31)
    @Pattern(regexp = "[^/\\\\?*\\[\\]:]+", message = "Sheet name contains invalid characters (/, \\, ?, *, [, ], :)")
    private String name;
    private Integer displayOrder;
}
