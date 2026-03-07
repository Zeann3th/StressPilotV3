package dev.zeann3th.stresspilot.ui.restful;

import dev.zeann3th.stresspilot.core.services.WebhookService;
import dev.zeann3th.stresspilot.ui.restful.exception.ResponseWrapper;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/webhooks")
@RequiredArgsConstructor
@ResponseWrapper
@Tag(name = "Webhooks", description = "API for executing load tests via YAML webhook")
public class WebhookController {

    private final WebhookService webhookService;

    @PostMapping(consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void execute(@RequestPart("file") MultipartFile file) {
        webhookService.execute(file);
    }
}
