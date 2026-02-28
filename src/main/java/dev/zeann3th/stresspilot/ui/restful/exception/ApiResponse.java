package dev.zeann3th.stresspilot.ui.restful.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.zeann3th.stresspilot.core.domain.enums.ErrorCode;
import io.opentelemetry.api.trace.Span;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        String errorType,
        String errorCode,
        String message,
        T data,
        String traceId
) {

    private static String getCurrentTraceId() {
        return Span.current().getSpanContext().getTraceId();
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(
                ErrorCode.ER0000.getErrorType().name(),
                ErrorCode.ER0000.name(),
                ErrorCode.ER0000.getMessage(),
                data,
                getCurrentTraceId()
        );
    }

    public static <T> ApiResponse<T> error(ErrorCode errorCode, T data) {
        return new ApiResponse<>(
                errorCode.getErrorType().name(),
                errorCode.name(),
                errorCode.getMessage(),
                data,
                getCurrentTraceId()
        );
    }

    public static <T> ApiResponse<T> error(ErrorCode errorCode) {
        return error(errorCode, null);
    }
}