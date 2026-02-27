package dev.zeann3th.stresspilot.ui.grpc;

import dev.zeann3th.stresspilot.core.domain.entities.RunEntity;
import dev.zeann3th.stresspilot.core.services.runs.RunService;
import dev.zeann3th.stresspilot.grpc.ui.*;
import dev.zeann3th.stresspilot.ui.grpc.mappers.RunProtoMapper;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.grpc.server.service.GrpcService;

import java.util.List;

@Slf4j
@GrpcService
@RequiredArgsConstructor
public class RunGrpcService extends RunServiceGrpc.RunServiceImplBase {

    private final RunService runService;
    private final RunProtoMapper runProtoMapper;

    @Override
    public void listRuns(ListRunsRequest request, StreamObserver<ListRunsResponse> responseObserver) {
        List<RunEntity> runs = runService.getRunHistory(
                request.getFlowId() == 0 ? null : request.getFlowId());
        responseObserver.onNext(runProtoMapper.toListProto(runs));
        responseObserver.onCompleted();
    }

    @Override
    public void getRun(GetRunRequest request, StreamObserver<RunResponse> responseObserver) {
        RunEntity entity = runService.getRunDetail(request.getId());
        responseObserver.onNext(runProtoMapper.toProto(entity));
        responseObserver.onCompleted();
    }

    @Override
    public void getLastRun(GetLastRunRequest request, StreamObserver<RunResponse> responseObserver) {
        RunEntity entity = runService.getLastRun(request.getFlowId());
        responseObserver.onNext(runProtoMapper.toProto(entity));
        responseObserver.onCompleted();
    }
}
