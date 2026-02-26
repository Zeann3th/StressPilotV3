package dev.zeann3th.stresspilot.core.services.endpoints;

import dev.zeann3th.stresspilot.core.domain.commands.endpoint.*;
import dev.zeann3th.stresspilot.core.domain.entities.EndpointEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

public interface EndpointService {
    Page<EndpointEntity> getAllEndpoints(Long projectId, String name, Pageable pageable);

    EndpointEntity getEndpointById(Long endpointId);

    EndpointEntity createEndpoint(CreateEndpointCommand endpointDTO);

    EndpointEntity updateEndpoint(Long endpointId, Map<String, Object> endpointUpdateRequest);

    void deleteEndpoint(Long endpointId);

    void uploadEndpoints(MultipartFile file, Long projectId);

    ExecuteEndpointResponse runEndpoint(Long endpointId, ExecuteEndpointCommand request);

    ExecuteEndpointResponse runAdhocEndpoint(Long projectId, ExecuteAdhocEndpointCommand requestBody);
}
