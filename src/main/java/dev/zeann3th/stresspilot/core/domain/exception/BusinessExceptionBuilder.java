package dev.zeann3th.stresspilot.core.domain.exception;

import dev.zeann3th.stresspilot.core.domain.enums.ErrorCode;

import java.util.Map;

public class BusinessExceptionBuilder {
    public static BusinessException exception(ErrorCode errorCode, Map<String, Object> params) {
        throw new BusinessException(errorCode, params);
    }

    public static BusinessException exception(ErrorCode errorCode) {
        throw new BusinessException(errorCode);
    }

    private BusinessExceptionBuilder() {

    }
}
