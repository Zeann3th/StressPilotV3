package dev.zeann3th.stresspilot.core.domain.enums;

import lombok.Getter;

@Getter
public enum ErrorCode {
    SP0000(ErrorType.SUCCESS, "Success"),
    SP0001(ErrorType.FAILURE, "Bad request"),
    SP0002(ErrorType.FAILURE, "Project not found"),
    SP0003(ErrorType.FAILURE, "Flow not found"),
    SP0004(ErrorType.FAILURE, "Error parsing flow steps"),
    SP0005(ErrorType.FAILURE, "Endpoint not found"),
    SP0006(ErrorType.FAILURE, "Error parsing endpoints from specification"),
    SP0007(ErrorType.FAILURE, "Unsupported endpoint specification format"),
    SP0008(ErrorType.FAILURE, "gRPC method not found in specification"),
    SP0009(ErrorType.FAILURE, "Unsupported executor type"),
    SP0010(ErrorType.FAILURE, "Run not found"),
    SP0011(ErrorType.FAILURE, "Error importing project from file"),
    SP0012(ErrorType.FAILURE, "Error exporting project to file"),
    SP0013(ErrorType.FAILURE, "Error downloading JDBC driver"),
    SP0014(ErrorType.FAILURE, "Invalid file format"),
    SP0015(ErrorType.FAILURE, "Error connecting to data source"),
    SP9999(ErrorType.FAILURE, "System is busy, please try again later"),;

    private final ErrorType errorType;
    private final String message;

    ErrorCode(ErrorType errorType, String message) {
        this.errorType = errorType;
        this.message = message;
    }
}