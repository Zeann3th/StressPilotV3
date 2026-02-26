package dev.zeann3th.stresspilot.ui.restful.controllers;

import dev.zeann3th.stresspilot.core.services.ConfigService;
import dev.zeann3th.stresspilot.ui.restful.dtos.config.ConfigDTO;
import dev.zeann3th.stresspilot.ui.restful.exception.ResponseWrapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/config")
@RequiredArgsConstructor
@ResponseWrapper
public class ConfigController {

    private final ConfigService configService;

    @GetMapping
    public Map<String, String> getAllConfigs() {
        return configService.getAllConfigs();
    }

    @GetMapping("/value")
    public String getConfigValue(@RequestParam("key") String key) {
        return configService.getValue(key)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setConfigValue(@Valid @RequestBody ConfigDTO configDTO) {
        configService.setValue(configDTO.getKey(), configDTO.getValue());
    }
}
