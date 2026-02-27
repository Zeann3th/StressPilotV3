package dev.zeann3th.stresspilot.ui.grpc;

import com.google.protobuf.Empty;
import dev.zeann3th.stresspilot.core.domain.commands.endpoint.*;
import dev.zeann3th.stresspilot.core.domain.entities.EndpointEntity;
import dev.zeann3th.stresspilot.core.services.endpoints.EndpointService;
import dev.zeann3th.stresspilot.grpc.ui.*;
import dev.zeann3th.stresspilot.ui.grpc.mappers.EndpointProtoMapper;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.grpc.server.service.GrpcService;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

@Slf4j
@GrpcService
@RequiredArgsConstructor
public class EndpointGrpcService extends EndpointServiceGrpc.EndpointServiceImplBase {

    private final EndpointService endpointService;
    private final EndpointProtoMapper endpointProtoMapper;
    private final JsonMapper jsonMapper;

    @Override
    public void listEndpoints(ListEndpointsRequest request, StreamObserver<ListEndpointsResponse> responseObserver) {
        Pageable pageable = toPageable(request.getPage(), request.getSize(), request.getSort());
        Page<EndpointEntity> page = endpointService.getAllEndpoints(
                request.getProjectId() == 0 ? null : request.getProjectId(),
                request.getName().isBlank() ? null : request.getName(),
                pageable);
        responseObserver.onNext(endpointProtoMapper.toListProto(
                page.getContent(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.getNumber(),
                page.getSize()));
        responseObserver.onCompleted();
    }

    @Override
    public void getEndpoint(GetEndpointRequest request, StreamObserver<EndpointResponse> responseObserver) {
        EndpointEntity entity = endpointService.getEndpointById(request.getId());
        responseObserver.onNext(endpointProtoMapper.toProto(entity));
        responseObserver.onCompleted();
    }

    @Override
    public void createEndpoint(CreateEndpointRequest request, StreamObserver<EndpointResponse> responseObserver) {
        CreateEndpointCommand cmd = endpointProtoMapper.toCreateCommand(request);
        EndpointEntity entity = endpointService.createEndpoint(cmd);
        responseObserver.onNext(endpointProtoMapper.toProto(entity));
        responseObserver.onCompleted();
    }

    @Override
    public void updateEndpoint(UpdateEndpointRequest request, StreamObserver<EndpointResponse> responseObserver) {
        Map<String, Object> patch = buildPatchMap(request);
        EndpointEntity entity = endpointService.updateEndpoint(request.getId(), patch);
        responseObserver.onNext(endpointProtoMapper.toProto(entity));
        responseObserver.onCompleted();
    }

    @Override
    public void deleteEndpoint(DeleteEndpointRequest request, StreamObserver<Empty> responseObserver) {
        endpointService.deleteEndpoint(request.getId());
        responseObserver.onNext(Empty.getDefaultInstance());
        responseObserver.onCompleted();
    }

    @Override
    public void executeEndpoint(ExecuteEndpointRequest request,
            StreamObserver<dev.zeann3th.stresspilot.grpc.ui.ExecuteEndpointResponse> responseObserver) {
        ExecuteEndpointCommand cmd = endpointProtoMapper.toExecuteCommand(request);
        dev.zeann3th.stresspilot.core.domain.commands.endpoint.ExecuteEndpointResponse result = endpointService
                .runEndpoint(request.getEndpointId(), cmd);
        responseObserver.onNext(endpointProtoMapper.toProto(result));
        responseObserver.onCompleted();
    }

    @Override
    public void executeAdhocEndpoint(ExecuteAdhocEndpointRequest request,
            StreamObserver<dev.zeann3th.stresspilot.grpc.ui.ExecuteEndpointResponse> responseObserver) {
        ExecuteAdhocEndpointCommand cmd = endpointProtoMapper.toAdhocCommand(request);
        dev.zeann3th.stresspilot.core.domain.commands.endpoint.ExecuteEndpointResponse result = endpointService
                .runAdhocEndpoint(request.getProjectId(), cmd);
        responseObserver.onNext(endpointProtoMapper.toProto(result));
        responseObserver.onCompleted();
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private static Map<String, Object> buildPatchMap(UpdateEndpointRequest r) {
        Map<String, Object> patch = new java.util.LinkedHashMap<>();
        if (!r.getName().isBlank())
            patch.put("name", r.getName());
        if (!r.getDescription().isBlank())
            patch.put("description", r.getDescription());
        if (!r.getUrl().isBlank())
            patch.put("url", r.getUrl());
        if (!r.getBodyJson().isBlank())
            patch.put("body", r.getBodyJson());
        if (!r.getSuccessCondition().isBlank())
            patch.put("successCondition", r.getSuccessCondition());
        if (!r.getHttpMethod().isBlank())
            patch.put("httpMethod", r.getHttpMethod());
        if (!r.getHttpHeadersJson().isBlank())
            patch.put("httpHeaders", r.getHttpHeadersJson());
        if (!r.getHttpParamsJson().isBlank())
            patch.put("httpParameters", r.getHttpParamsJson());
        if (!r.getGrpcServiceName().isBlank())
            patch.put("grpcServiceName", r.getGrpcServiceName());
        if (!r.getGrpcMethodName().isBlank())
            patch.put("grpcMethodName", r.getGrpcMethodName());
        return patch;
    }

    private static Pageable toPageable(int page, int size, String sort) {
        int safePage = page <= 0 ? 0 : page;
        int safeSize = size <= 0 ? 20 : size;
        if (sort != null && !sort.isBlank()) {
            return PageRequest.of(safePage, safeSize, Sort.by(sort));
        }
        return PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
    }
}
