package dev.zeann3th.stresspilot.ui.restful;

import dev.zeann3th.stresspilot.ui.restful.exception.ResponseWrapper;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/utilities")
@RequiredArgsConstructor
@ResponseWrapper
public class UtilityController {
    private static final String SESSION_ID = "sessionId";

    @GetMapping("/session")
    public Map<String, String> getSession(HttpSession httpSession) {
        return Map.of(SESSION_ID, httpSession.getId());
    }
}
