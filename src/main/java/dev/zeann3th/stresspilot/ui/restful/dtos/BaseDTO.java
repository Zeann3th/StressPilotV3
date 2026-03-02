package dev.zeann3th.stresspilot.ui.restful.dtos;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class BaseDTO {
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
