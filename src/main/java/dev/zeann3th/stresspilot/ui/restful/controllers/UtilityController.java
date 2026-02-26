package dev.zeann3th.stresspilot.ui.restful.controllers;

import dev.zeann3th.stresspilot.ui.restful.exception.ResponseWrapper;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/utilities")
@RequiredArgsConstructor
@ResponseWrapper
public class UtilityController {

    @GetMapping("/session")
    public String getSession(HttpSession httpSession) {
        return httpSession.getId();
    }
}
