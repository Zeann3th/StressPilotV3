package dev.zeann3th.stresspilot.ui.restful.validators;

import dev.zeann3th.stresspilot.ui.restful.dtos.endpoint.CreateEndpointRequestDTO;
import jakarta.validation.ConstraintValidatorContext;
import org.pf4j.ExtensionPoint;

public interface EndpointTypeValidator extends ExtensionPoint {
    boolean supports(String endpointType);

    boolean isValid(CreateEndpointRequestDTO request, ConstraintValidatorContext context);
}
