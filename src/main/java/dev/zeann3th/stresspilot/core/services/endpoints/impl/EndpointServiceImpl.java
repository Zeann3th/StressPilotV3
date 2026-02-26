package dev.zeann3th.stresspilot.core.services.endpoints.impl;

import dev.zeann3th.stresspilot.core.domain.commands.endpoint.CreateEndpointCommand;
import dev.zeann3th.stresspilot.core.domain.commands.endpoint.ExecuteAdhocEndpointCommand;
import dev.zeann3th.stresspilot.core.domain.commands.endpoint.ExecuteEndpointCommand;
import dev.zeann3th.stresspilot.core.domain.commands.endpoint.ExecuteEndpointResponse;
import dev.zeann3th.stresspilot.core.domain.entities.EndpointEntity;
import dev.zeann3th.stresspilot.core.services.endpoints.EndpointService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class EndpointServiceImpl implements EndpointService {
    @Override
    public Page<EndpointEntity> getListEndpoint(Long projectId, String name, Pageable pageable) {
        return null;
    }

    @Override
    public EndpointEntity getEndpointDetail(Long endpointId) {
        return null;
    }

    @Override
    public EndpointEntity createEndpoint(CreateEndpointCommand endpointDTO) {
        return null;
    }

    @Override
    public EndpointEntity updateEndpoint(Long endpointId, Map<String, Object> endpointUpdateRequest) {
        return null;
    }

    @Override
    public void deleteEndpoint(Long endpointId) {

    }

    @Override
    public void uploadEndpoints(MultipartFile file, Long projectId) {

    }

    @Override
    public ExecuteEndpointResponse runEndpoint(Long endpointId, ExecuteEndpointCommand request) {
        return null;
    }

    @Override
    public ExecuteEndpointResponse runAdhocEndpoint(Long projectId, ExecuteAdhocEndpointCommand requestBody) {
        return null;
    }
}
