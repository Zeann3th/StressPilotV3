package dev.zeann3th.stresspilot.ui.grpc.mappers;

import dev.zeann3th.stresspilot.core.domain.commands.endpoint.*;
import dev.zeann3th.stresspilot.core.domain.entities.EndpointEntity;
import dev.zeann3th.stresspilot.grpc.ui.*;
import dev.zeann3th.stresspilot.infrastructure.configs.MapstructProtoConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(config = MapstructProtoConfig.class)
public interface EndpointProtoMapper {

        @Mapping(source = "project.id", target = "projectId")
        EndpointResponse toProto(EndpointEntity entity);

        @Mapping(source = "bodyJson", target = "body", qualifiedByName = "mapToObjectMap")
        @Mapping(source = "httpHeadersJson", target = "httpHeaders", qualifiedByName = "mapToObjectMap")
        @Mapping(source = "httpParamsJson", target = "httpParameters", qualifiedByName = "mapToObjectMap")
        CreateEndpointCommand toCreateCommand(CreateEndpointRequest request);

        @Mapping(source = "bodyJson", target = "body", qualifiedByName = "mapToObjectMap")
        @Mapping(source = "httpHeadersJson", target = "httpHeaders", qualifiedByName = "mapToStringMap")
        @Mapping(source = "httpParamsJson", target = "httpParameters", qualifiedByName = "mapToStringMap")
        ExecuteAdhocEndpointCommand toAdhocCommand(ExecuteAdhocEndpointRequest request);

        default ExecuteEndpointCommand toExecuteCommand(ExecuteEndpointRequest request) {
                return ExecuteEndpointCommand.builder()
                                .variables(new java.util.HashMap<>(request.getVariablesMap()))
                                .build();
        }

        @org.mapstruct.Named("mapToObjectMap")
        default java.util.Map<String, Object> mapToObjectMap(String value) {
                if (value == null || value.isBlank())
                        return new java.util.HashMap<>();
                try {
                        return new tools.jackson.databind.ObjectMapper().readValue(value,
                                        new tools.jackson.core.type.TypeReference<>() {
                                        });
                } catch (Exception _) {
                        return java.util.Map.of("error", value);
                }
        }

        @org.mapstruct.Named("mapToStringMap")
        default java.util.Map<String, String> mapToStringMap(String value) {
                if (value == null || value.isBlank())
                        return new java.util.HashMap<>();
                try {
                        return new tools.jackson.databind.ObjectMapper().readValue(value,
                                        new tools.jackson.core.type.TypeReference<>() {
                                        });
                } catch (Exception _) {
                        return java.util.Map.of("error", value);
                }
        }

        @Mapping(source = "responseTimeMs", target = "responseTimeMs")
        @Mapping(expression = "java(response.getData() != null ? response.getData().toString() : \"\")", target = "dataJson")
        dev.zeann3th.stresspilot.grpc.ui.ExecuteEndpointResponse toProto(
                        dev.zeann3th.stresspilot.core.domain.commands.endpoint.ExecuteEndpointResponse response);

        default ListEndpointsResponse toListProto(List<EndpointEntity> entities,
                        long totalElements,
                        int totalPages,
                        int page,
                        int size) {
                return ListEndpointsResponse.newBuilder()
                                .addAllEndpoints(entities.stream().map(this::toProto).toList())
                                .setTotalElements(totalElements)
                                .setTotalPages(totalPages)
                                .setPage(page)
                                .setSize(size)
                                .build();
        }
}
