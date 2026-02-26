package dev.zeann3th.stresspilot.ui.restful.validators;

import dev.zeann3th.stresspilot.ui.restful.dtos.endpoint.CreateEndpointRequestDTO;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.extern.slf4j.Slf4j;
import org.pf4j.PluginManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class EndpointRequestValidator implements ConstraintValidator<ValidEndpointRequest, CreateEndpointRequestDTO> {

    private final PluginManager pluginManager;
    private final List<EndpointTypeValidator> validators;

    @Autowired
    public EndpointRequestValidator(List<EndpointTypeValidator> validators, PluginManager pluginManager) {
        this.validators = validators;
        this.pluginManager = pluginManager;
    }

    @Override
    public boolean isValid(CreateEndpointRequestDTO request, ConstraintValidatorContext context) {
        if (request == null || request.getType() == null) {
            return true;
        }

        String type = request.getType().toUpperCase().trim();
        log.debug("Validating endpoint type: {}", type);

        // 1. Check built-in Spring Bean validators (HTTP, JDBC, etc.)
        for (EndpointTypeValidator validator : validators) {
            if (validator.supports(type)) {
                log.debug("Found built-in validator for: {}", type);
                return validator.isValid(request, context);
            }
        }

        // 2. Check PF4J Plugin Extensions
        try {
            List<EndpointTypeValidator> extensions = pluginManager.getExtensions(EndpointTypeValidator.class);
            log.info("PF4J lookup for {}. Found {} extensions.", type, extensions.size());

            for (EndpointTypeValidator extension : extensions) {
                if (extension.supports(type)) {
                    log.info("Found plugin extension validator for: {}", type);
                    return extension.isValid(request, context);
                }
            }
        } catch (Exception e) {
            log.error("Error retrieving plugin extensions during validation", e);
        }

        log.warn("No validator found for endpoint type: {}", type);

        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate("Unsupported or invalid endpoint type: " + type)
                .addPropertyNode("type")
                .addConstraintViolation();

        return false;
    }
}
