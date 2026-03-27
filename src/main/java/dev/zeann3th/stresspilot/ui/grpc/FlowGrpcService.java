package dev.zeann3th.stresspilot.ui.grpc;

import com.google.protobuf.Empty;
import dev.zeann3th.stresspilot.grpc.ui.RunFlowResponse;
import dev.zeann3th.stresspilot.core.domain.commands.flow.FlowStepCommand;
import dev.zeann3th.stresspilot.core.domain.entities.FlowEntity;
import dev.zeann3th.stresspilot.core.domain.commands.flow.RunFlowCommand;
import dev.zeann3th.stresspilot.core.services.flows.FlowService;
import dev.zeann3th.stresspilot.grpc.ui.*;
import dev.zeann3th.stresspilot.ui.grpc.mappers.FlowProtoMapper;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.grpc.server.service.GrpcService;

import java.util.List;
import java.util.Map;

@Slf4j
@GrpcService
@RequiredArgsConstructor
public class FlowGrpcService extends FlowServiceGrpc.FlowServiceImplBase {

    private final FlowService flowService;
    private final FlowProtoMapper flowProtoMapper;

    @Override
    public void listFlows(ListFlowsRequest request, StreamObserver<ListFlowsResponse> responseObserver) {
        Pageable pageable = toPageable(request.getPage(), request.getSize(), request.getSort());
        Page<FlowEntity> page = flowService.getListFlow(
                request.getProjectId() == 0 ? null : request.getProjectId(),
                request.getName().isBlank() ? null : request.getName(),
                pageable);
        responseObserver.onNext(flowProtoMapper.toListProto(
                page.getContent(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.getNumber(),
                page.getSize()));
        responseObserver.onCompleted();
    }

    @Override
    public void getFlow(GetFlowRequest request, StreamObserver<FlowResponse> responseObserver) {
        FlowEntity entity = flowService.getFlowDetail(request.getId());
        responseObserver.onNext(flowProtoMapper.toProto(entity));
        responseObserver.onCompleted();
    }

    @Override
    public void createFlow(CreateFlowRequest request, StreamObserver<FlowResponse> responseObserver) {
        FlowEntity entity = flowService.createFlow(flowProtoMapper.toCreateCommand(request));
        responseObserver.onNext(flowProtoMapper.toProto(entity));
        responseObserver.onCompleted();
    }

    @Override
    public void updateFlow(UpdateFlowRequest request, StreamObserver<FlowResponse> responseObserver) {
        Map<String, Object> patch = new java.util.LinkedHashMap<>();
        if (!request.getName().isBlank())
            patch.put("name", request.getName());
        if (!request.getDescription().isBlank())
            patch.put("description", request.getDescription());
        FlowEntity entity = flowService.updateFlow(request.getId(), patch);
        responseObserver.onNext(flowProtoMapper.toProto(entity));
        responseObserver.onCompleted();
    }

    @Override
    public void deleteFlow(DeleteFlowRequest request, StreamObserver<Empty> responseObserver) {
        flowService.deleteFlow(request.getId());
        responseObserver.onNext(Empty.getDefaultInstance());
        responseObserver.onCompleted();
    }

    @Override
    public void configureFlow(ConfigureFlowRequest request, StreamObserver<ConfigureFlowResponse> responseObserver) {
        List<FlowStepCommand> stepCommands = flowProtoMapper.toStepCommands(request.getStepsList());
        FlowEntity configured = flowService.configureFlow(request.getFlowId(), stepCommands);
        ConfigureFlowResponse response = ConfigureFlowResponse.newBuilder()
                .addAllSteps(configured.getSteps().stream()
                        .map(flowProtoMapper::toStepProto)
                        .toList())
                .build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void runFlow(RunFlowRequest request, StreamObserver<RunFlowResponse> responseObserver) {
        RunFlowCommand cmd = flowProtoMapper.toRunCommand(request);
        String runId = flowService.runFlow(request.getFlowId(), cmd);
        responseObserver.onNext(RunFlowResponse.newBuilder().setRunId(runId).build());
        responseObserver.onCompleted();
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private static Pageable toPageable(int page, int size, String sort) {
        int safePage = page <= 0 ? 0 : page;
        int safeSize = size <= 0 ? 20 : size;
        if (sort != null && !sort.isBlank()) {
            return PageRequest.of(safePage, safeSize, Sort.by(sort));
        }
        return PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
    }
}
