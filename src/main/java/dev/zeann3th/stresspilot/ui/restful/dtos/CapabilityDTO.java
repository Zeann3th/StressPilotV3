package dev.zeann3th.stresspilot.ui.restful.dtos;

import dev.zeann3th.stresspilot.core.domain.commands.ParserCapability;

import java.util.List;

public record CapabilityDTO(
    List<String> endpointExecutors,
    List<String> flowExecutors,
    List<ParserCapability> parsers
) {}
