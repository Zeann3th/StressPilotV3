package dev.zeann3th.stresspilot.ui.grpc;

import com.google.protobuf.Empty;
import dev.zeann3th.stresspilot.core.domain.enums.ErrorCode;
import dev.zeann3th.stresspilot.core.domain.exception.CommandException;
import dev.zeann3th.stresspilot.core.services.configs.ConfigService;
import dev.zeann3th.stresspilot.grpc.ui.*;
import dev.zeann3th.stresspilot.ui.grpc.mappers.ConfigProtoMapper;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.grpc.server.service.GrpcService;

@Slf4j
@GrpcService
@RequiredArgsConstructor
public class ConfigGrpcService extends ConfigServiceGrpc.ConfigServiceImplBase {

    private final ConfigService configService;
    private final ConfigProtoMapper configProtoMapper;

    @Override
    public void getAllConfigs(Empty request, StreamObserver<GetAllConfigsResponse> responseObserver) {
        responseObserver.onNext(configProtoMapper.toProto(configService.getAllConfigs()));
        responseObserver.onCompleted();
    }

    @Override
    public void getConfigValue(GetConfigValueRequest request, StreamObserver<GetConfigValueResponse> responseObserver) {
        String value = configService.getValue(request.getKey())
                .orElseThrow(() -> new CommandException(ErrorCode.ER0001,
                        java.util.Map.of("reason", "Config key not found: " + request.getKey())));
        responseObserver.onNext(configProtoMapper.toValueProto(value));
        responseObserver.onCompleted();
    }

    @Override
    public void setConfigValue(SetConfigValueRequest request, StreamObserver<Empty> responseObserver) {
        configService.setValue(request.getKey(), request.getValue());
        responseObserver.onNext(Empty.getDefaultInstance());
        responseObserver.onCompleted();
    }
}
