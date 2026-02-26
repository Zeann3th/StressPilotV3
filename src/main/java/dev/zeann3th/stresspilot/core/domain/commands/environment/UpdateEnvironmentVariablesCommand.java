package dev.zeann3th.stresspilot.core.domain.commands.environment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Command carrying the changes to apply to an environment's variables.
 * Mirrors V1's UpdateEnvironmentRequestDTO but lives in the domain commands package.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateEnvironmentVariablesCommand {

    /** IDs of variables to permanently delete. */
    private List<Long> removed;

    /** Variables whose key/value/active state should be changed. */
    private List<Update> updated;

    /** New key-value pairs to add. */
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
