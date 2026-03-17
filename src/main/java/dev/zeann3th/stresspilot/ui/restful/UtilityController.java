package dev.zeann3th.stresspilot.ui.restful;

import dev.zeann3th.stresspilot.core.services.executors.EndpointExecutorFactory;
import dev.zeann3th.stresspilot.core.services.flows.FlowExecutorFactory;
import dev.zeann3th.stresspilot.core.services.parsers.endpoints.ParserServiceFactory;
import dev.zeann3th.stresspilot.ui.restful.dtos.CapabilityDTO;
import dev.zeann3th.stresspilot.ui.restful.exception.ResponseWrapper;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/utilities")
@RequiredArgsConstructor
@ResponseWrapper
@Tag(name = "Utilities", description = "Utility endpoints for session management and capability discovery")
public class UtilityController {
    private final EndpointExecutorFactory endpointExecutorFactory;
    private final FlowExecutorFactory flowExecutorFactory;
    private final ParserServiceFactory parserServiceFactory;

    @GetMapping("/session")
    public String getSession(HttpSession httpSession) {
        return httpSession.getId();
    }

    @GetMapping("/capabilities")
    public CapabilityDTO getCapabilities() {
        return new CapabilityDTO(
                endpointExecutorFactory.listTypes(),
                flowExecutorFactory.listTypes(),
                parserServiceFactory.listTypes()
        );
    }
}
