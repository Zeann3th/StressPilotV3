package dev.zeann3th.stresspilot.ui.restful.validators.strategies;

import dev.zeann3th.stresspilot.core.domain.enums.EndpointType;
import dev.zeann3th.stresspilot.ui.restful.dtos.endpoint.CreateEndpointRequestDTO;
import dev.zeann3th.stresspilot.ui.restful.validators.EndpointTypeValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class JsEndpointValidator implements EndpointTypeValidator {
    @Override
    public boolean supports(String endpointType) {
        return EndpointType.JS.name().equalsIgnoreCase(endpointType);
    }

    @Override
    public boolean isValid(CreateEndpointRequestDTO request, ConstraintValidatorContext context) {
        if (request.getBody() == null || request.getBody().isBlank()) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("JS endpoint requires a script in the body")
                    .addPropertyNode("body")
                    .addConstraintViolation();
            return false;
        }
        return true;
    }
}
