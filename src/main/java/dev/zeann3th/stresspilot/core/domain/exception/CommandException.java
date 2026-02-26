package dev.zeann3th.stresspilot.core.domain.exception;

import dev.zeann3th.stresspilot.core.domain.enums.ErrorCode;
import lombok.Getter;

import java.util.Map;

@Getter
public class CommandException extends RuntimeException {
    private final ErrorCode errorCode;

    public CommandException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public CommandException(ErrorCode errorCodes, Map<String, Object> params) {
        super(interpolate(errorCodes.getMessage(), params));
        this.errorCode = errorCodes;
    }

    private static String interpolate(String message, Map<String, Object> params) {
        if (params == null || params.isEmpty()) return message;

        String result = message;
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            String placeholder = "<" + entry.getKey() + ">";
            result = result.replace(placeholder, String.valueOf(entry.getValue()));
        }

        return result;
    }

}
