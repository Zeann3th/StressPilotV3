package dev.zeann3th.stresspilot.ui.restful.exception;

import dev.zeann3th.stresspilot.core.domain.enums.ErrorCode;
import dev.zeann3th.stresspilot.core.domain.exception.CommandException;
import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Optional;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class HttpGlobalExceptionHandler {

    private final ObjectProvider<Tracer> tracerProvider;

    @ExceptionHandler(value = CommandException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(CommandException ex) {
        ErrorCode errorCode = ex.getErrorCode();

        ApiResponse<Void> response = new ApiResponse<>(
                errorCode.getErrorType().name(),
                errorCode.name(),
                ex.getMessage(),
                null,
                getTraceId()
        );

        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @ExceptionHandler(value = Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception ex) {
        log.error("Unhandled exception: ", ex);

        ApiResponse<Void> response = new ApiResponse<>(
                ErrorCode.SP9999.getErrorType().name(),
                ErrorCode.SP9999.name(),
                ErrorCode.SP9999.getMessage(),
                null,
                getTraceId()
        );

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    private String getTraceId() {
        return Optional.ofNullable(tracerProvider.getIfAvailable())
                .map(Tracer::currentSpan)
                .map(span -> span.context().traceId())
                .orElse(null);
    }
}