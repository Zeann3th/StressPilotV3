package dev.zeann3th.stresspilot.ui.grpc.exception;

import dev.zeann3th.stresspilot.core.domain.enums.ErrorCode;
import dev.zeann3th.stresspilot.core.domain.exception.CommandException;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusException;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.grpc.server.exception.GrpcExceptionHandler;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class GrpcGlobalExceptionHandler implements GrpcExceptionHandler {

    private static final Metadata.Key<String> ERROR_CODE_KEY = Metadata.Key.of("x-error-code",
            Metadata.ASCII_STRING_MARSHALLER);
    private static final Metadata.Key<String> ERROR_TYPE_KEY = Metadata.Key.of("x-error-type",
            Metadata.ASCII_STRING_MARSHALLER);

    @Override
    public @Nullable StatusException handleException(@NonNull Throwable exception) {
        if (exception instanceof CommandException ex) {
            ErrorCode errorCode = ex.getErrorCode();

            Metadata metadata = new Metadata();
            metadata.put(ERROR_CODE_KEY, errorCode.name());
            metadata.put(ERROR_TYPE_KEY, errorCode.getErrorType().name());

            Status status = mapToGrpcStatus(errorCode)
                    .withDescription(ex.getMessage())
                    .withCause(ex);

            return status.asException(metadata);
        }

        log.error("Unhandled gRPC exception", exception);

        Metadata metadata = new Metadata();
        metadata.put(ERROR_CODE_KEY, ErrorCode.ER9999.name());
        metadata.put(ERROR_TYPE_KEY, ErrorCode.ER9999.getErrorType().name());

        return Status.INTERNAL
                .withDescription(ErrorCode.ER9999.getMessage())
                .withCause(exception)
                .asException(metadata);
    }

    private Status mapToGrpcStatus(ErrorCode code) {
        return switch (code) {
            case ER0002, ER0003, ER0005, ER0010, ER0016, ER0017 -> Status.NOT_FOUND;
            case ER0001, ER0007, ER0009, ER0014, ER0019, ER0020, ER0021, ER0022 -> Status.INVALID_ARGUMENT;
            case ER0018 -> Status.ALREADY_EXISTS;
            case ER9999 -> Status.UNAVAILABLE;
            default -> Status.INTERNAL;
        };
    }
}
