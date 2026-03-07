package dev.zeann3th.stresspilot.ui.grpc;

import com.google.protobuf.Empty;
import dev.zeann3th.stresspilot.core.services.WebhookService;
import dev.zeann3th.stresspilot.grpc.ui.WebhookExecuteRequest;
import dev.zeann3th.stresspilot.grpc.ui.WebhookServiceGrpc;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.grpc.server.service.GrpcService;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;

@Slf4j
@GrpcService
@RequiredArgsConstructor
public class WebhookGrpcService extends WebhookServiceGrpc.WebhookServiceImplBase {

    private final WebhookService webhookService;

    @Override
    public void execute(WebhookExecuteRequest request, StreamObserver<Empty> responseObserver) {
        MultipartFile file = new MultipartFile() {
            @Override
            public @NonNull String getName() { return "file"; }

            @Override
            public String getOriginalFilename() {
                return request.getFilename().isBlank() ? "webhook.yaml" : request.getFilename();
            }

            @Override
            public String getContentType() { return "application/x-yaml"; }

            @Override
            public boolean isEmpty() { return request.getContent().isEmpty(); }

            @Override
            public long getSize() { return request.getContent().size(); }

            @Override
            public byte @NonNull [] getBytes() { return request.getContent().toByteArray(); }

            @Override
            public @NonNull InputStream getInputStream() { return new ByteArrayInputStream(getBytes()); }

            @Override
            public void transferTo(@NonNull File dest) {
                throw new UnsupportedOperationException();
            }
        };

        webhookService.execute(file);
        responseObserver.onNext(Empty.getDefaultInstance());
        responseObserver.onCompleted();
    }
}
