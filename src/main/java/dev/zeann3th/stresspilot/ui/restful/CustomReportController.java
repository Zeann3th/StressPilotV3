package dev.zeann3th.stresspilot.ui.restful;

import dev.zeann3th.stresspilot.core.domain.entities.CustomReportElementEntity;
import dev.zeann3th.stresspilot.core.domain.entities.CustomReportSheetEntity;
import dev.zeann3th.stresspilot.core.services.report.CustomReportService;
import dev.zeann3th.stresspilot.ui.restful.dtos.report.CustomReportElementRequestDTO;
import dev.zeann3th.stresspilot.ui.restful.dtos.report.CustomReportElementResponseDTO;
import dev.zeann3th.stresspilot.ui.restful.dtos.report.CustomReportSheetRequestDTO;
import dev.zeann3th.stresspilot.ui.restful.dtos.report.CustomReportSheetResponseDTO;
import dev.zeann3th.stresspilot.ui.restful.exception.ResponseWrapper;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/report-sheets")
@RequiredArgsConstructor
@ResponseWrapper
@Tag(name = "Custom Report Sheets", description = "CRUD for user-defined custom Excel report sheets")
public class CustomReportController {

    private final CustomReportService customReportService;

    @GetMapping
    public List<CustomReportSheetResponseDTO> getAllSheets() {
        return customReportService.getAllSheets().stream().map(this::toSheetResponse).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CustomReportSheetResponseDTO createSheet(@RequestBody CustomReportSheetRequestDTO req) {
        return toSheetResponse(customReportService.createSheet(
                req.getName(), req.getDisplayOrder() != null ? req.getDisplayOrder() : 0));
    }

    @PatchMapping("/{id}")
    public CustomReportSheetResponseDTO updateSheet(@PathVariable Long id,
                                                     @RequestBody CustomReportSheetRequestDTO req) {
        return toSheetResponse(customReportService.updateSheet(id, req.getName(), req.getDisplayOrder()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSheet(@PathVariable Long id) {
        customReportService.deleteSheet(id);
    }

    @PostMapping("/{id}/elements")
    @ResponseStatus(HttpStatus.CREATED)
    public CustomReportElementResponseDTO createElement(@PathVariable Long id,
                                                         @RequestBody CustomReportElementRequestDTO req) {
        return toElementResponse(customReportService.createElement(
                id, req.getName(), req.getType(), req.getConfig(),
                req.getDisplayOrder() != null ? req.getDisplayOrder() : 0));
    }

    @PatchMapping("/{id}/elements/{eid}")
    public CustomReportElementResponseDTO updateElement(@PathVariable Long id,
                                                         @PathVariable Long eid,
                                                         @RequestBody CustomReportElementRequestDTO req) {
        return toElementResponse(customReportService.updateElement(
                id, eid, req.getName(), req.getType(), req.getConfig(), req.getDisplayOrder()));
    }

    @DeleteMapping("/{id}/elements/{eid}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteElement(@PathVariable Long id, @PathVariable Long eid) {
        customReportService.deleteElement(id, eid);
    }

    private CustomReportSheetResponseDTO toSheetResponse(CustomReportSheetEntity entity) {
        CustomReportSheetResponseDTO dto = new CustomReportSheetResponseDTO();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setDisplayOrder(entity.getDisplayOrder());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        dto.setElements(entity.getElements() == null ? List.of()
                : entity.getElements().stream().map(this::toElementResponse).toList());
        return dto;
    }

    private CustomReportElementResponseDTO toElementResponse(CustomReportElementEntity entity) {
        CustomReportElementResponseDTO dto = new CustomReportElementResponseDTO();
        dto.setId(entity.getId());
        dto.setSheetId(entity.getSheet() != null ? entity.getSheet().getId() : null);
        dto.setName(entity.getName());
        dto.setType(entity.getType().name());
        dto.setConfig(entity.getConfig());
        dto.setDisplayOrder(entity.getDisplayOrder());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }
}
