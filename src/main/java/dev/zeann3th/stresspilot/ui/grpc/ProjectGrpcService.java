package dev.zeann3th.stresspilot.ui.grpc;

import com.google.protobuf.Empty;
import dev.zeann3th.stresspilot.core.domain.entities.ProjectEntity;
import dev.zeann3th.stresspilot.core.services.projects.ProjectService;
import dev.zeann3th.stresspilot.grpc.ui.*;
import dev.zeann3th.stresspilot.ui.grpc.mappers.ProjectProtoMapper;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.grpc.server.service.GrpcService;
import org.springframework.web.multipart.MultipartFile;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.io.IOException;

@Slf4j
@GrpcService
@RequiredArgsConstructor
public class ProjectGrpcService extends ProjectServiceGrpc.ProjectServiceImplBase {

    private final ProjectService projectService;
    private final ProjectProtoMapper projectProtoMapper;

    @Override
    public void listProjects(ListProjectsRequest request, StreamObserver<ListProjectsResponse> responseObserver) {
        Pageable pageable = toPageable(request.getPage(), request.getSize(), request.getSort());
        Page<ProjectEntity> page = projectService.getListProject(
                request.getName().isBlank() ? null : request.getName(),
                pageable);
        responseObserver.onNext(projectProtoMapper.toListProto(
                page.getContent(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.getNumber(),
                page.getSize()));
        responseObserver.onCompleted();
    }

    @Override
    public void getProject(GetProjectRequest request, StreamObserver<ProjectResponse> responseObserver) {
        ProjectEntity entity = projectService.getProjectDetail(request.getId());
        responseObserver.onNext(projectProtoMapper.toProto(entity));
        responseObserver.onCompleted();
    }

    @Override
    public void createProject(CreateProjectRequest request, StreamObserver<ProjectResponse> responseObserver) {
        ProjectEntity entity = projectService.createProject(projectProtoMapper.toCreateCommand(request));
        responseObserver.onNext(projectProtoMapper.toProto(entity));
        responseObserver.onCompleted();
    }

    @Override
    public void updateProject(UpdateProjectRequest request, StreamObserver<ProjectResponse> responseObserver) {
        ProjectEntity entity = projectService.updateProject(
                request.getId(),
                projectProtoMapper.toUpdateCommand(request));
        responseObserver.onNext(projectProtoMapper.toProto(entity));
        responseObserver.onCompleted();
    }

    @Override
    public void deleteProject(DeleteProjectRequest request, StreamObserver<Empty> responseObserver) {
        projectService.deleteProject(request.getId());
        responseObserver.onNext(Empty.getDefaultInstance());
        responseObserver.onCompleted();
    }

    @Override
    public void exportProject(ExportProjectRequest request, StreamObserver<ExportProjectResponse> responseObserver) {
        ByteArrayResource resource = projectService.exportProject(request.getId());
        responseObserver.onNext(ExportProjectResponse.newBuilder()
                .setContent(com.google.protobuf.ByteString.copyFrom(resource.getByteArray()))
                .setFilename("project_" + request.getId() + ".json")
                .build());
        responseObserver.onCompleted();
    }

    @Override
    public void importProject(ImportProjectRequest request, StreamObserver<ProjectResponse> responseObserver) {
        MultipartFile file = new MultipartFile() {
            @Override
            public String getName() {
                return "file";
            }

            @Override
            public String getOriginalFilename() {
                return request.getFilename().isBlank() ? "import.json" : request.getFilename();
            }

            @Override
            public String getContentType() {
                return "application/json";
            }

            @Override
            public boolean isEmpty() {
                return request.getContent().isEmpty();
            }

            @Override
            public long getSize() {
                return request.getContent().size();
            }

            @Override
            public byte[] getBytes() throws IOException {
                return request.getContent().toByteArray();
            }

            @Override
            public InputStream getInputStream() throws IOException {
                return new ByteArrayInputStream(request.getContent().toByteArray());
            }

            @Override
            public void transferTo(File dest) throws IOException, IllegalStateException {
                throw new UnsupportedOperationException();
            }
        };
        ProjectEntity entity = projectService.importProject(file);
        responseObserver.onNext(projectProtoMapper.toProto(entity));
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
