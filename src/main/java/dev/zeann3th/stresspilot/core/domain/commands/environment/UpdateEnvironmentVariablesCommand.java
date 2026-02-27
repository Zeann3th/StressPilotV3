package dev.zeann3th.stresspilot.core.domain.commands.environment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateEnvironmentVariablesCommand {

    private List<Long> removed;
    private List<Update> updated;
    private List<Add> added;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Update {
        private Long id;
        private String key;
        private String value;
        private boolean active;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Add {
        private String key;
        private String value;
    }
}
