package dev.zeann3th.stresspilot.ui.restful.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.zeann3th.stresspilot.core.domain.enums.ErrorCode;
import org.slf4j.MDC;

import java.util.Optional;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        String errorType,
        String errorCode,
        String message,
        T data,
        String traceId
) {

    private static String getCurrentTraceId() {
        return MDC.get("traceId");
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(
                ErrorCode.SP0000.getErrorType().name(),
                ErrorCode.SP0000.name(),
                ErrorCode.SP0000.getMessage(),
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