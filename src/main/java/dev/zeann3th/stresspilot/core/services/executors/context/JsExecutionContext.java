package dev.zeann3th.stresspilot.core.services.executors.context;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class JsExecutionContext {
    private String functionName;
    private List<Object> functionArgs;
    private List<String> userDefinedFunctions;
}
