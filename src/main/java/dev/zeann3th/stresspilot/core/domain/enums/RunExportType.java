package dev.zeann3th.stresspilot.core.domain.enums;

public enum RunExportType {
    XLSX,
    HTML;

    public static RunExportType fromRequest(String value) {
        if (value == null || value.isBlank()) {
            return XLSX;
        }
        String normalized = value.trim().toUpperCase();
        if ("EXCEL".equals(normalized)) {
            return XLSX;
        }
        return RunExportType.valueOf(normalized);
    }
}
