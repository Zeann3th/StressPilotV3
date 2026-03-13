package dev.zeann3th.stresspilot.core.domain.enums;

import lombok.Getter;

@Getter
public enum ErrorCode {
    ER0000(ErrorType.SUCCESS, "Success"),
    ER0001(ErrorType.FAILURE, "Bad request: <reason>"),
    ER0002(ErrorType.FAILURE, "Project not found"),
    ER0003(ErrorType.FAILURE, "Flow not found"),
    ER0004(ErrorType.FAILURE, "Error parsing flow steps: <reason>"),
    ER0005(ErrorType.FAILURE, "Endpoint not found: <id>"),
    ER0006(ErrorType.FAILURE, "Error parsing endpoints from specification: <reason>"),
    ER0007(ErrorType.FAILURE, "Unsupported endpoint specification format: <reason>"),
    ER0008(ErrorType.FAILURE, "gRPC method not found in specification"),
    ER0009(ErrorType.FAILURE, "Unsupported executor type"),
    ER0010(ErrorType.FAILURE, "Run not found"),
    ER0011(ErrorType.FAILURE, "Error importing project from file"),
    ER0012(ErrorType.FAILURE, "Error exporting project to file"),
    ER0013(ErrorType.FAILURE, "Error downloading JDBC driver"),
    ER0014(ErrorType.FAILURE, "Invalid file format: <reason>"),
    ER0015(ErrorType.FAILURE, "Error connecting to data source"),
    ER0016(ErrorType.FAILURE, "Environment not found: <id>"),
    ER0017(ErrorType.FAILURE, "Environment variable not found: <id>"),
    ER0018(ErrorType.FAILURE, "Duplicate environment variable key: <key>"),
    ER0019(ErrorType.FAILURE, "Invalid patch value: <reason>"),
    ER0020(ErrorType.FAILURE, "Invalid flow configuration: <reason>"),
    ER0021(ErrorType.FAILURE, "No handler registered for flow step type: <type>"),
    ER0022(ErrorType.FAILURE, "Invalid report type: <type>. Accepted values are DETAILED, SUMMARY"),
    ER0023(ErrorType.FAILURE, "Cannot export report for a run that is still running"),
    ER0024(ErrorType.FAILURE, "Failed to reload plugin: <reason>"),
    ER9999(ErrorType.FAILURE, "System is busy, please try again later"),;

    private final ErrorType errorType;
    private final String message;

    ErrorCode(ErrorType errorType, String message) {
        this.errorType = errorType;
        this.message = message;
    }
}