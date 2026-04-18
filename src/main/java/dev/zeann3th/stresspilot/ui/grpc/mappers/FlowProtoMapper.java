package dev.zeann3th.stresspilot.ui.grpc.mappers;

import dev.zeann3th.stresspilot.core.domain.commands.flow.*;
import dev.zeann3th.stresspilot.core.domain.entities.FlowEntity;
import dev.zeann3th.stresspilot.core.domain.entities.FlowStepEntity;
import dev.zeann3th.stresspilot.grpc.ui.*;
import dev.zeann3th.stresspilot.infrastructure.configs.mappers.MapstructProtoConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(config = MapstructProtoConfig.class)
public interface FlowProtoMapper {

        @Mapping(expression = "java(entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : \"\")", target = "createdAt")
        @Mapping(expression = "java(entity.getUpdatedAt() != null ? entity.getUpdatedAt().toString() : \"\")", target = "updatedAt")
        FlowResponse toProto(FlowEntity entity);

        FlowStepMessage toStepProto(FlowStepEntity step);

        CreateFlowCommand toCreateCommand(CreateFlowRequest request);

        FlowStepCommand toStepCommand(FlowStepMessage message);

        List<FlowStepCommand> toStepCommands(List<FlowStepMessage> messages);

        default java.util.Map<String, Object> map(String value) {
                if (value == null || value.isBlank()) {
                        return new java.util.HashMap<>();
                }
                try {
                        return new tools.jackson.databind.ObjectMapper().readValue(
                                        value,
                                        new tools.jackson.core.type.TypeReference<>() {
                                        });
                } catch (Exception _) {
                        return java.util.Map.of("error", value);
                }
        }

        default RunFlowCommand toRunCommand(RunFlowRequest request) {
                List<java.util.Map<String, Object>> credentials = request.getCredentialsList().stream()
                                .map(e -> new java.util.HashMap<String, Object>(e.getEntriesMap()))
                                .collect(java.util.stream.Collectors.toList());

                java.util.Map<String, Object> variables = new java.util.HashMap<>(request.getVariablesMap());

                return RunFlowCommand.builder()
                                .threads(request.getThreads())
                                .totalDuration(request.getTotalDuration())
                                .rampUpDuration(request.getRampUpDuration())
                                .metricsEndpoint(request.getMetricsEndpoint())
                                .variables(variables)
                                .credentials(credentials)
                                .build();
        }

        default ListFlowsResponse toListProto(List<FlowEntity> entities,
                        long totalElements,
                        int totalPages,
                        int page,
                        int size) {
                return ListFlowsResponse.newBuilder()
                                .addAllFlows(entities.stream().map(this::toProto).toList())
                                .setTotalElements(totalElements)
                                .setTotalPages(totalPages)
                                .setPage(page)
                                .setSize(size)
                                .build();
        }
}
