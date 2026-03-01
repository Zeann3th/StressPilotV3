package dev.zeann3th.stresspilot.ui.restful.validators.strategies;

import dev.zeann3th.stresspilot.core.domain.enums.EndpointType;
import dev.zeann3th.stresspilot.ui.restful.dtos.endpoint.CreateEndpointRequestDTO;
import dev.zeann3th.stresspilot.ui.restful.validators.EndpointTypeValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.stereotype.Component;

@Component
public class HttpEndpointValidator implements EndpointTypeValidator {

    @Override
    public boolean supports(String endpointType) {
        return EndpointType.HTTP.name().equalsIgnoreCase(endpointType);
    }

    @Override
    public boolean isValid(CreateEndpointRequestDTO request, ConstraintValidatorContext context) {
        boolean valid = request.getHttpMethod() != null;
        if (request.getUrl() == null)
            valid = false;

        if (!valid) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("Missing required field for HTTP request (method, url)")
                    .addConstraintViolation();
        }
        return valid;
    }
}
