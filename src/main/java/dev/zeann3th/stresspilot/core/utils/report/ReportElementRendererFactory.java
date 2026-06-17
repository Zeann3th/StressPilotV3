package dev.zeann3th.stresspilot.core.utils.report;

import dev.zeann3th.stresspilot.core.domain.enums.ReportElementType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class ReportElementRendererFactory {
    private final Map<ReportElementType, ReportElementRenderer> renderers;

    public ReportElementRendererFactory(List<ReportElementRenderer> rendererList) {
        renderers = new EnumMap<>(ReportElementType.class);
        rendererList.forEach(r -> renderers.put(r.supports(), r));
    }

    public ReportElementRenderer get(ReportElementType type) {
        ReportElementRenderer renderer = renderers.get(type);
        if (renderer == null) {
            throw new IllegalArgumentException("No renderer registered for type: " + type);
        }
        return renderer;
    }
}
