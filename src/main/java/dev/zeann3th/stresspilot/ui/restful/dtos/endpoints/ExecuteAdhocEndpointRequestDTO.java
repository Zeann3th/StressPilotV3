package dev.zeann3th.stresspilot.ui.restful.dtos.endpoints;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class ExecuteAdhocEndpointRequestDTO extends ExecuteEndpointRequestDTO {
    @NotNull
    private String type;
}
