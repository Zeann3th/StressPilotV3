package dev.zeann3th.stresspilot.core.services.report;

import dev.zeann3th.stresspilot.core.domain.entities.CustomReportElementEntity;
import dev.zeann3th.stresspilot.core.domain.entities.CustomReportSheetEntity;
import dev.zeann3th.stresspilot.core.domain.enums.ErrorCode;
import dev.zeann3th.stresspilot.core.domain.enums.ReportElementType;
import dev.zeann3th.stresspilot.core.domain.exception.CommandExceptionBuilder;
import dev.zeann3th.stresspilot.core.ports.store.CustomReportSheetStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomReportServiceImpl implements CustomReportService {

    private final CustomReportSheetStore sheetStore;

    @Override
    public List<CustomReportSheetEntity> getAllSheets() {
        return sheetStore.findAll();
    }

    @Override
    @Transactional
    public CustomReportSheetEntity createSheet(String name, int displayOrder) {
        return sheetStore.save(CustomReportSheetEntity.builder()
                .name(name)
                .displayOrder(displayOrder)
                .build());
    }

    @Override
    @Transactional
    public CustomReportSheetEntity updateSheet(Long id, String name, Integer displayOrder) {
        CustomReportSheetEntity sheet = sheetStore.findById(id)
                .orElseThrow(() -> CommandExceptionBuilder.exception(ErrorCode.ER0002));
        if (name != null) sheet.setName(name);
        if (displayOrder != null) sheet.setDisplayOrder(displayOrder);
        return sheetStore.save(sheet);
    }

    @Override
    @Transactional
    public void deleteSheet(Long id) {
        if (!sheetStore.existsById(id)) {
            throw CommandExceptionBuilder.exception(ErrorCode.ER0002);
        }
        sheetStore.deleteById(id);
    }

    @Override
    @Transactional
    public CustomReportElementEntity createElement(Long sheetId, String name, String type, String config, int displayOrder) {
        CustomReportSheetEntity sheet = sheetStore.findById(sheetId)
                .orElseThrow(() -> CommandExceptionBuilder.exception(ErrorCode.ER0002));
        CustomReportElementEntity element = CustomReportElementEntity.builder()
                .sheet(sheet)
                .name(name)
                .type(ReportElementType.valueOf(type.toUpperCase()))
                .config(config)
                .displayOrder(displayOrder)
                .build();
        sheet.getElements().add(element);
        CustomReportSheetEntity saved = sheetStore.save(sheet);
        return saved.getElements().stream()
                .filter(e -> e.getName().equals(name) && e.getDisplayOrder() == displayOrder)
                .findFirst()
                .orElseThrow();
    }

    @Override
    @Transactional
    public CustomReportElementEntity updateElement(Long sheetId, Long elementId, String name, String type, String config, Integer displayOrder) {
        CustomReportSheetEntity sheet = sheetStore.findById(sheetId)
                .orElseThrow(() -> CommandExceptionBuilder.exception(ErrorCode.ER0002));
        CustomReportElementEntity element = sheet.getElements().stream()
                .filter(e -> e.getId().equals(elementId))
                .findFirst()
                .orElseThrow(() -> CommandExceptionBuilder.exception(ErrorCode.ER0002));
        if (name != null) element.setName(name);
        if (type != null) element.setType(ReportElementType.valueOf(type.toUpperCase()));
        if (config != null) element.setConfig(config);
        if (displayOrder != null) element.setDisplayOrder(displayOrder);
        sheetStore.save(sheet);
        return element;
    }

    @Override
    @Transactional
    public void deleteElement(Long sheetId, Long elementId) {
        CustomReportSheetEntity sheet = sheetStore.findById(sheetId)
                .orElseThrow(() -> CommandExceptionBuilder.exception(ErrorCode.ER0002));
        sheet.getElements().removeIf(e -> e.getId().equals(elementId));
        sheetStore.save(sheet);
    }
}
