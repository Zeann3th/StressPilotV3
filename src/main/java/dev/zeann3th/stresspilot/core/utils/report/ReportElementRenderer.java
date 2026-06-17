package dev.zeann3th.stresspilot.core.utils.report;

import dev.zeann3th.stresspilot.core.domain.enums.ReportElementType;
import org.apache.poi.xssf.streaming.SXSSFSheet;

public interface ReportElementRenderer {
    ReportElementType supports();

    /**
     * Renders this element starting at startRow.
     * @return the number of rows consumed (next element starts at startRow + return value)
     */
    int render(SXSSFSheet sheet, int startRow, ElementRenderContext ctx) throws Exception;
}
