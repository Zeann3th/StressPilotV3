package dev.zeann3th.stresspilot.ui.grpc.mappers;

import dev.zeann3th.stresspilot.grpc.ui.*;
import dev.zeann3th.stresspilot.infrastructure.configs.MapstructProtoConfig;
import org.mapstruct.Mapper;

import java.util.Map;

@Mapper(config = MapstructProtoConfig.class)
public interface ConfigProtoMapper {

    default GetAllConfigsResponse toProto(Map<String, String> configs) {
        return GetAllConfigsResponse.newBuilder()
                .putAllConfigs(configs)
                .build();
    }

    default GetConfigValueResponse toValueProto(String value) {
        return GetConfigValueResponse.newBuilder()
                .setValue(value)
                .build();
    }
}
