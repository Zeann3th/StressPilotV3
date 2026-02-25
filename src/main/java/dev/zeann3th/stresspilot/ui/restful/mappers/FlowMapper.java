package dev.zeann3th.stresspilot.ui.restful.mappers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.zeann3th.stresspilot.core.domain.commands.flow.*;
import dev.zeann3th.stresspilot.core.domain.entities.FlowEntity;
import dev.zeann3th.stresspilot.core.domain.entities.FlowStepEntity;
import dev.zeann3th.stresspilot.infrastructure.configs.MapstructConfig;
import dev.zeann3th.stresspilot.ui.restful.dtos.flows.*;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Mapper(config = MapstructConfig.class)
public abstract class FlowMapper {

    @Autowired
    protected ObjectMapper objectMapper;

    @Mapping(target = "projectId", source = "project.id")
    @Mapping(target = "steps", source = "steps")
    public abstract FlowResponseDTO toDTO(FlowEntity entity);

    public abstract List<FlowResponseDTO> toDTOList(List<FlowEntity> entityList);

    @Mapping(target = "endpointId", source = "endpoint.id")
    @Mapping(target = "preProcessor", source = "preProcessor", qualifiedByName = "jsonToMap")
    @Mapping(target = "postProcessor", source = "postProcessor", qualifiedByName = "jsonToMap")
    public abstract FlowStepDTO toDTO(FlowStepEntity entity);

    public abstract List<FlowStepDTO> toDTOListFromEntities(List<FlowStepEntity> entityList);

    public abstract CreateFlowCommand toCreateCommand(CreateFlowRequestDTO dto);

    public abstract FlowStepCommand toStepCommand(FlowStepDTO dto);

    public abstract List<FlowStepCommand> toStepCommandList(List<FlowStepDTO> dtoList);

    public abstract FlowStepDTO toStepDTO(FlowStepResponse response);

    public abstract List<FlowStepDTO> toStepDTOList(List<FlowStepResponse> responseList);

    @Mapping(target = "flowId", source = "flowId")
    @Mapping(target = "threads", source = "dto.threads")
    @Mapping(target = "totalDuration", source = "dto.totalDuration")
    @Mapping(target = "rampUpDuration", source = "dto.rampUpDuration")
    @Mapping(target = "variables", source = "dto.variables")
    @Mapping(target = "credentials", source = "credentials")
    public abstract RunFlowCommand toRunCommand(Long flowId, RunFlowRequestDTO dto, List<Map<String, Object>> credentials);

    @Named("jsonToMap")
    protected Map<String, Object> jsonToMap(String json) {
        if (json == null || json.isBlank()) return Collections.emptyMap();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            return Collections.emptyMap();
        }
    }
}
