package dev.zeann3th.stresspilot.core.services;

import dev.zeann3th.stresspilot.core.domain.commands.endpoint.*;
import dev.zeann3th.stresspilot.core.domain.entities.EndpointEntity;
import dev.zeann3th.stresspilot.core.domain.commands.endpoint.EndpointResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

public interface EndpointService {
    Page<EndpointEntity> getListEndpoint(Long projectId, String name, Pageable pageable);

    EndpointEntity getEndpointDetail(Long endpointId);

    EndpointEntity createEndpoint(CreateEndpointCommand command);

    EndpointEntity updateEndpoint(UpdateEndpointCommand command);

    void deleteEndpoint(Long endpointId);

    void uploadEndpoints(MultipartFile file, Long projectId);

    EndpointResponse runEndpoint(ExecuteEndpointCommand command);

    EndpointResponse runAdhocEndpoint(ExecuteAdhocEndpointCommand command);
}
