package dev.zeann3th.stresspilot.core.domain.commands.webhook;

import dev.zeann3th.stresspilot.core.domain.commands.project.ImportProjectCommand;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class WebhookCommand extends ImportProjectCommand {

    private RunConfig run;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RunConfig {
        private int threads;
        private int duration;
        private int rampUpDuration;
    }
}
