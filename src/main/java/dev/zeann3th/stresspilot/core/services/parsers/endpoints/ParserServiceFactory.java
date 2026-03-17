package dev.zeann3th.stresspilot.core.services.parsers.endpoints;

import dev.zeann3th.stresspilot.core.domain.commands.ParserCapability;
import dev.zeann3th.stresspilot.core.domain.constants.Constants;
import dev.zeann3th.stresspilot.core.domain.enums.ErrorCode;
import dev.zeann3th.stresspilot.core.domain.exception.CommandExceptionBuilder;
import lombok.RequiredArgsConstructor;
import org.pf4j.spring.SpringPluginManager;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
public class ParserServiceFactory {

    private final List<ParserService> parsers;
    private final SpringPluginManager pluginManager;

    public ParserService getParser(String filename, String contentType, String content) {
        return Stream.concat(parsers.stream(), pluginManager.getExtensions(ParserService.class).stream())
                .filter(parser -> parser.supports(filename, contentType, content))
                .findFirst()
                .orElseThrow(() -> CommandExceptionBuilder.exception(ErrorCode.ER0006,
                        Map.of(Constants.REASON, "No parser registered for given input: " + filename)));
    }

    public List<ParserCapability> listTypes() {
        return Stream.concat(parsers.stream(), pluginManager.getExtensions(ParserService.class).stream())
                .map(ParserService::getCapability)
                .toList();
    }
}
