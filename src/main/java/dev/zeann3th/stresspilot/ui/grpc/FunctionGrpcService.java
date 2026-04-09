package dev.zeann3th.stresspilot.ui.grpc;

import com.google.protobuf.Empty;
import dev.zeann3th.stresspilot.core.domain.entities.FunctionEntity;
import dev.zeann3th.stresspilot.core.services.functions.FunctionService;
import dev.zeann3th.stresspilot.grpc.ui.*;
import dev.zeann3th.stresspilot.ui.grpc.mappers.FunctionProtoMapper;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.grpc.server.service.GrpcService;

@Slf4j
@GrpcService
@RequiredArgsConstructor
public class FunctionGrpcService extends FunctionServiceGrpc.FunctionServiceImplBase {

    private final FunctionService functionService;
    private final FunctionProtoMapper functionProtoMapper;

    @Override
    public void listFunctions(ListFunctionsRequest request, StreamObserver<ListFunctionsResponse> responseObserver) {
        Pageable pageable = toPageable(request.getPage(), request.getSize(), request.getSort());
        Page<FunctionEntity> page = functionService.getListFunction(
                request.getName().isBlank() ? null : request.getName(),
                pageable);
        responseObserver.onNext(functionProtoMapper.toListProto(
                page.getContent(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.getNumber(),
                page.getSize()));
        responseObserver.onCompleted();
    }

    @Override
    public void getFunction(GetFunctionRequest request, StreamObserver<FunctionResponse> responseObserver) {
        FunctionEntity entity = functionService.getFunctionDetail(request.getId());
        responseObserver.onNext(functionProtoMapper.toProto(entity));
        responseObserver.onCompleted();
    }

    @Override
    public void createFunction(CreateFunctionRequest request, StreamObserver<FunctionResponse> responseObserver) {
        FunctionEntity entity = functionService.createFunction(functionProtoMapper.toCreateCommand(request));
        responseObserver.onNext(functionProtoMapper.toProto(entity));
        responseObserver.onCompleted();
    }

    @Override
    public void updateFunction(UpdateFunctionRequest request, StreamObserver<FunctionResponse> responseObserver) {
        FunctionEntity entity = functionService.updateFunction(request.getId(), functionProtoMapper.toUpdateCommand(request));
        responseObserver.onNext(functionProtoMapper.toProto(entity));
        responseObserver.onCompleted();
    }

    @Override
    public void deleteFunction(DeleteFunctionRequest request, StreamObserver<Empty> responseObserver) {
        functionService.deleteFunction(request.getId());
        responseObserver.onNext(Empty.getDefaultInstance());
        responseObserver.onCompleted();
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
