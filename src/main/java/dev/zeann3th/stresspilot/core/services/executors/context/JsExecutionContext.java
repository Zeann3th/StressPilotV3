package dev.zeann3th.stresspilot.core.services.executors.context;

import lombok.Getter;
import lombok.Setter;
import org.graalvm.polyglot.Context;

import java.util.List;

@Getter
@Setter
public class JsExecutionContext implements AutoCloseable {
    private String functionName;
    private List<Object> functionArgs;
    private List<String> userDefinedFunctions;
    private Context graalContext;

    @Override
    public void close() {
        if (graalContext != null) {
            try {
                graalContext.close();
            } catch (Exception ignored) {
            }
            graalContext = null;
        }
    }
}
