package dev.zeann3th.stresspilot.ui.restful.mappers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.zeann3th.stresspilot.core.domain.commands.endpoint.*;
import dev.zeann3th.stresspilot.core.domain.entities.EndpointEntity;
import dev.zeann3th.stresspilot.infrastructure.configs.MapstructConfig;
import dev.zeann3th.stresspilot.ui.restful.dtos.endpoints.*;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Mapper(config = MapstructConfig.class)
public abstract class EndpointMapper {

    @Autowired
    protected ObjectMapper objectMapper;

    @Mapping(target = "projectId", source = "project.id")
    @Mapping(target = "httpHeaders", source = "httpHeaders", qualifiedByName = "jsonToMap")
    @Mapping(target = "httpParameters", source = "httpParameters", qualifiedByName = "jsonToMap")
    @Mapping(target = "body", source = "body", qualifiedByName = "jsonToObject")
    public abstract EndpointDTO toDTO(EndpointEntity entity);

    public abstract List<EndpointDTO> toDTOList(List<EndpointEntity> entityList);

    @Mapping(target = "projectId", source = "projectId")
    public abstract CreateEndpointCommand toCreateCommand(EndpointDTO dto);

    @Mapping(target = "endpointId", source = "endpointId")
    @Mapping(target = "url", source = "dto.url")
    @Mapping(target = "body", source = "dto.body")
    @Mapping(target = "httpHeaders", source = "dto.httpHeaders")
    @Mapping(target = "httpParameters", source = "dto.httpParameters")
    @Mapping(target = "variables", source = "dto.variables")
    public abstract ExecuteEndpointCommand toExecuteCommand(Long endpointId, ExecuteEndpointRequestDTO dto);

    @Mapping(target = "projectId", source = "projectId")
    public abstract ExecuteAdhocEndpointCommand toExecuteAdhocCommand(Long projectId, ExecuteAdhocEndpointRequestDTO dto);

    public abstract EndpointResponseDTO toResponseDTO(EndpointResponse response);

    @Named("jsonToMap")
    protected Map<String, Object> jsonToMap(String json) {
        if (json == null || json.isBlank()) return Collections.emptyMap();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            return Collections.emptyMap();
        }
    }

    @Named("jsonToObject")
    protected Object jsonToObject(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (JsonProcessingException e) {
            return json;
        }
    }
}
