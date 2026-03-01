package dev.zeann3th.stresspilot.ui.restful.validators.strategies;

import dev.zeann3th.stresspilot.core.domain.enums.EndpointType;
import dev.zeann3th.stresspilot.ui.restful.dtos.endpoint.CreateEndpointRequestDTO;
import dev.zeann3th.stresspilot.ui.restful.validators.EndpointTypeValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.stereotype.Component;

@Component
public class GrpcEndpointValidator implements EndpointTypeValidator {

    @Override
    public boolean supports(String endpointType) {
        return EndpointType.GRPC.name().equalsIgnoreCase(endpointType);
    }

    @Override
    public boolean isValid(CreateEndpointRequestDTO request, ConstraintValidatorContext context) {
        boolean valid = request.getGrpcServiceName() != null;
        if (request.getGrpcMethodName() == null)
            valid = false;
        if (request.getGrpcStubPath() == null)
            valid = false;

        if (!valid) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                    "Missing required field for gRPC request (serviceName, methodName, stubPath)")
                    .addConstraintViolation();
        }
        return valid;
    }
}
