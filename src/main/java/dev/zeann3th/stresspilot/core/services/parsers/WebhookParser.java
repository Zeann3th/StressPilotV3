package dev.zeann3th.stresspilot.core.services.parsers;

import dev.zeann3th.stresspilot.core.domain.commands.webhook.WebhookCommand;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class WebhookParser extends ProjectParser {

    public WebhookCommand unmarshalWebhook(String spec) {
        Map<String, Object> root = parseRoot(spec);
        Map<String, Object> runNode = getMap(root, "run");

        WebhookCommand cmd = new WebhookCommand();
        var base = unmarshal(spec);
        cmd.setName(base.getName());
        cmd.setDescription(base.getDescription());
        cmd.setEnvironment(base.getEnvironment());
        cmd.setEndpoints(base.getEndpoints());
        cmd.setFlows(base.getFlows());

        if (!runNode.isEmpty()) {
            cmd.setRun(WebhookCommand.RunConfig.builder()
                    .threads(getInt(runNode, "threads", 1))
                    .duration(getInt(runNode, "duration", 60))
                    .rampUpDuration(getInt(runNode, "ramp_up_duration", 0))
                    .build());
        }

        return cmd;
    }
}
