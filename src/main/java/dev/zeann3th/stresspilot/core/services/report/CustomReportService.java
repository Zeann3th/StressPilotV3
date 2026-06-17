package dev.zeann3th.stresspilot.core.services.report;

import dev.zeann3th.stresspilot.core.domain.entities.CustomReportElementEntity;
import dev.zeann3th.stresspilot.core.domain.entities.CustomReportSheetEntity;

import java.util.List;

public interface CustomReportService {
    List<CustomReportSheetEntity> getAllSheets();
    CustomReportSheetEntity createSheet(String name, int displayOrder);
    CustomReportSheetEntity updateSheet(Long id, String name, Integer displayOrder);
    void deleteSheet(Long id);

    CustomReportElementEntity createElement(Long sheetId, String name, String type, String config, int displayOrder);
    CustomReportElementEntity updateElement(Long sheetId, Long elementId, String name, String type, String config, Integer displayOrder);
    void deleteElement(Long sheetId, Long elementId);
}
