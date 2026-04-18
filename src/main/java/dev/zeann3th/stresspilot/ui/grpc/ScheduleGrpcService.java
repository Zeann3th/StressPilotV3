package dev.zeann3th.stresspilot.ui.grpc;

import com.google.protobuf.Empty;
import dev.zeann3th.stresspilot.core.domain.entities.ScheduleEntity;
import dev.zeann3th.stresspilot.core.services.jobs.ScheduleService;
import dev.zeann3th.stresspilot.grpc.ui.*;
import dev.zeann3th.stresspilot.ui.grpc.mappers.ScheduleProtoMapper;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.grpc.server.service.GrpcService;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@GrpcService
@RequiredArgsConstructor
public class ScheduleGrpcService extends ScheduleServiceGrpc.ScheduleServiceImplBase {

    private final ScheduleService scheduleService;
    private final ScheduleProtoMapper scheduleProtoMapper;

    @Override
    public void listSchedules(ListSchedulesRequest request, StreamObserver<ListSchedulesResponse> responseObserver) {
        Pageable pageable = PageRequest.of(Math.max(request.getPage(), 0), request.getSize() <= 0 ? 20 : request.getSize(), Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<ScheduleEntity> page = scheduleService.getListSchedule(pageable);
        responseObserver.onNext(scheduleProtoMapper.toListProto(
                page.getContent(),
                page.getTotalElements(),
                page.getTotalPages()));
        responseObserver.onCompleted();
    }

    @Override
    public void getSchedule(GetScheduleRequest request, StreamObserver<ScheduleResponse> responseObserver) {
        ScheduleEntity entity = scheduleService.getScheduleDetail(request.getId());
        responseObserver.onNext(scheduleProtoMapper.toProto(entity));
        responseObserver.onCompleted();
    }

    @Override
    public void createSchedule(CreateScheduleRequest request, StreamObserver<ScheduleResponse> responseObserver) {
        ScheduleEntity entity = scheduleProtoMapper.toEntity(request);
        ScheduleEntity resp = scheduleService.createSchedule(entity);
        responseObserver.onNext(scheduleProtoMapper.toProto(resp));
        responseObserver.onCompleted();
    }

    @Override
    public void updateSchedule(UpdateScheduleRequest request, StreamObserver<ScheduleResponse> responseObserver) {
        Map<String, Object> patch = new LinkedHashMap<>();
        if (!request.getQuartzExpr().isBlank()) patch.put("quartzExpr", request.getQuartzExpr());
        if (request.getThreads() > 0) patch.put("threads", request.getThreads());
        if (request.getDuration() > 0) patch.put("duration", request.getDuration());
        if (request.getRampUp() >= 0) patch.put("rampUp", request.getRampUp());
        patch.put("enabled", request.getEnabled());

        ScheduleEntity entity = scheduleService.updateSchedule(request.getId(), patch);
        responseObserver.onNext(scheduleProtoMapper.toProto(entity));
        responseObserver.onCompleted();
    }

    @Override
    public void deleteSchedule(DeleteScheduleRequest request, StreamObserver<Empty> responseObserver) {
        scheduleService.deleteSchedule(request.getId());
        responseObserver.onNext(Empty.getDefaultInstance());
        responseObserver.onCompleted();
    }
}
