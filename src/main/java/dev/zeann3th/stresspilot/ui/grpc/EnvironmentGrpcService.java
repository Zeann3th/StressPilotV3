package dev.zeann3th.stresspilot.ui.grpc;

import com.google.protobuf.Empty;
import dev.zeann3th.stresspilot.core.domain.entities.EnvironmentVariableEntity;
import dev.zeann3th.stresspilot.core.services.environments.EnvironmentService;
import dev.zeann3th.stresspilot.grpc.ui.*;
import dev.zeann3th.stresspilot.ui.grpc.mappers.EnvironmentProtoMapper;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.grpc.server.service.GrpcService;

import java.util.List;

@Slf4j
@GrpcService
@RequiredArgsConstructor
public class EnvironmentGrpcService extends EnvironmentServiceGrpc.EnvironmentServiceImplBase {

    private final EnvironmentService environmentService;
    private final EnvironmentProtoMapper environmentProtoMapper;

    @Override
    public void getEnvironmentVariables(GetEnvironmentVariablesRequest request,
            StreamObserver<ListEnvironmentVariablesResponse> responseObserver) {
        List<EnvironmentVariableEntity> variables = environmentService
                .getEnvironmentVariables(request.getEnvironmentId());
        responseObserver.onNext(environmentProtoMapper.toListProto(variables));
        responseObserver.onCompleted();
    }

    @Override
    public void updateEnvironmentVariables(UpdateEnvironmentVariablesRequest request,
            StreamObserver<Empty> responseObserver) {
        environmentService.updateEnvironmentVariables(
                request.getEnvironmentId(),
                environmentProtoMapper.toUpdateCommand(request));
        responseObserver.onNext(Empty.getDefaultInstance());
        responseObserver.onCompleted();
    }
}
