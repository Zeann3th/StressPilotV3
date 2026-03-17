package dev.zeann3th.stresspilot.core.domain.commands;

import java.util.List;

public record ParserCapability(
        String name,
        List<String> formats
) {}
