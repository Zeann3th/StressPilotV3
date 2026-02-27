package dev.zeann3th.stresspilot.core.services.parsers;

import dev.zeann3th.stresspilot.core.domain.constants.Constants;
import dev.zeann3th.stresspilot.core.domain.enums.ErrorCode;
import dev.zeann3th.stresspilot.core.domain.exception.CommandExceptionBuilder;
import lombok.RequiredArgsConstructor;
import org.pf4j.spring.SpringPluginManager;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ParserServiceFactory {

    private final List<ParserService> parsers;
    private final SpringPluginManager pluginManager;

    public ParserService getParser(String type) {
        var internal = parsers.stream()
                .filter(parser -> parser.getType().equalsIgnoreCase(type))
                .findFirst();

        if (internal.isPresent()) {
            return internal.get();
        }

        List<ParserService> extensions = pluginManager.getExtensions(ParserService.class);
        return extensions.stream()
                .filter(parser -> parser.getType().equalsIgnoreCase(type))
                .findFirst()
                .orElseThrow(() -> CommandExceptionBuilder.exception(ErrorCode.ER0006,
                        Map.of(Constants.REASON, "No parser registered for type: " + type)));
    }
}
