package dev.zeann3th.stresspilot.core.utils.report;

import dev.zeann3th.stresspilot.core.domain.entities.CustomReportElementEntity;
import dev.zeann3th.stresspilot.core.domain.entities.CustomReportSheetEntity;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

import java.util.List;

@Slf4j(topic = "CustomSheetBuilder")
public class CustomSheetBuilder {

    private CustomSheetBuilder() {}

    public static void buildAll(
            SXSSFWorkbook workbook,
            List<CustomReportSheetEntity> sheets,
            ElementRenderContext baseCtx,
            ReportElementRendererFactory factory) {

        for (CustomReportSheetEntity sheetDef : sheets) {
            try {
                SXSSFSheet sheet = workbook.createSheet(sheetDef.getName());
                buildSheet(sheet, sheetDef, baseCtx, factory);
            } catch (Exception e) {
                log.warn("Custom sheet '{}' failed entirely: {}", sheetDef.getName(), e.getMessage());
                try {
                    String errName = "[ERROR] " + sheetDef.getName();
                    SXSSFSheet errSheet = workbook.createSheet(errName);
                    Row row = errSheet.createRow(0);
                    row.createCell(0).setCellValue("Sheet failed: " + e.getMessage());
                } catch (Exception ignored) {
                    // if even the error sheet fails, continue
                }
            }
        }
    }

    private static void buildSheet(
            SXSSFSheet sheet,
            CustomReportSheetEntity sheetDef,
            ElementRenderContext baseCtx,
            ReportElementRendererFactory factory) {

        int rowOffset = 0;
        for (CustomReportElementEntity elementDef : sheetDef.getElements()) {
            try {
                ReportElementRenderer renderer = factory.get(elementDef.getType());
                ElementRenderContext ctx = ElementRenderContext.builder()
                        .report(baseCtx.getReport())
                        .logs(baseCtx.getLogs())
                        .timeBuckets(baseCtx.getTimeBuckets())
                        .element(elementDef)
                        .build();
                int consumed = renderer.render(sheet, rowOffset, ctx);
                rowOffset += consumed;
            } catch (Exception e) {
                log.warn("Element '{}' in sheet '{}' failed: {}",
                        elementDef.getName(), sheetDef.getName(), e.getMessage());
                try {
                    Row errRow = sheet.createRow(rowOffset++);
                    errRow.createCell(0).setCellValue(
                            elementDef.getName() + " — RENDER ERROR: " + e.getMessage());
                } catch (Exception ignored) {}
            }
        }
    }
}
