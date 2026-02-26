package dev.zeann3th.stresspilot.ui.restful.dtos.environment;

import lombok.Data;
import java.util.List;

@Data
public class UpdateEnvironmentVariablesRequestDTO {
    private List<Long> removed;
    private List<UpdateEntry> updated;
    private List<AddEntry> added;

    @Data
    public static class UpdateEntry {
        private Long id;
        private String key;
        private String value;
        private boolean active;
    }

    @Data
    public static class AddEntry {
        private String key;
        private String value;
    }
}
