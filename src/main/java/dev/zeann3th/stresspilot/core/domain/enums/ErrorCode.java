package dev.zeann3th.stresspilot.core.domain.enums;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "Bad request"),
    PROJECT_NOT_FOUND(HttpStatus.NOT_FOUND, "Project not found"),
    FLOW_NOT_FOUND(HttpStatus.NOT_FOUND, "Flow not found"),
    FLOW_CONFIGURATION_ERROR(HttpStatus.BAD_REQUEST, "Error parsing flow steps"),
    ENDPOINT_NOT_FOUND(HttpStatus.NOT_FOUND, "Endpoint not found"),
    ENDPOINT_PARSE_ERROR(HttpStatus.BAD_REQUEST, "Error parsing endpoints from specification"),
    ENDPOINT_UNSUPPORTED_FORMAT(HttpStatus.BAD_REQUEST, "Unsupported endpoint specification format"),
    ENDPOINT_GRPC_METHOD_NOT_FOUND(HttpStatus.NOT_FOUND, "gRPC method not found in specification"),
    SYSTEM_BUSY(HttpStatus.INTERNAL_SERVER_ERROR, "System is busy, please try again later"),
    EXECUTOR_UNSUPPORTED_TYPE(HttpStatus.BAD_REQUEST, "Unsupported executor type"),
    RUN_NOT_FOUND(HttpStatus.NOT_FOUND, "Run not found"),
    PROJECT_IMPORT_ERROR(HttpStatus.BAD_REQUEST, "Error importing project from file"),
    PROJECT_EXPORT_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Error exporting project to file"),
    JDBC_DRIVER_DOWNLOAD_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Error downloading JDBC driver"),
    INVALID_FILE_FORMAT(HttpStatus.BAD_REQUEST, "Invalid file format"),
    DATASOURCE_CONNECTION_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Error connecting to data source");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }
}
