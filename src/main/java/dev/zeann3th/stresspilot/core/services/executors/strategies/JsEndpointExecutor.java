package dev.zeann3th.stresspilot.core.services.executors.strategies;

import dev.zeann3th.stresspilot.core.domain.commands.endpoint.ExecuteEndpointResponse;
import dev.zeann3th.stresspilot.core.domain.entities.EndpointEntity;
import dev.zeann3th.stresspilot.core.domain.entities.FunctionEntity;
import dev.zeann3th.stresspilot.core.domain.enums.EndpointType;
import dev.zeann3th.stresspilot.core.services.executors.EndpointExecutor;
import dev.zeann3th.stresspilot.core.services.executors.context.ExecutionContext;
import dev.zeann3th.stresspilot.core.services.executors.context.JsExecutionContext;
import dev.zeann3th.stresspilot.core.services.functions.FunctionService;
import dev.zeann3th.stresspilot.core.utils.DataUtils;
import dev.zeann3th.stresspilot.core.utils.MockDataUtils;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.graalvm.polyglot.*;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Slf4j(topic = "JsEndpointExecutor")
@RequiredArgsConstructor
public class JsEndpointExecutor implements EndpointExecutor {

    private final FunctionService functionService;

    private Engine engine;

    private Source udfSource;

    @PostConstruct
    public void init() {
        engine = Engine.newBuilder("js")
                .option("engine.WarnInterpreterOnly", "false")
                .build();
        loadUserDefinedFunctions();
    }

    public void loadUserDefinedFunctions() {
        List<FunctionEntity> functions = functionService.getAllFunctions();
        List<String> activeUdfs = functions.stream()
                .filter(f -> f.getActive() != null && f.getActive())
                .map(FunctionEntity::getBody)
                .toList();

        if (!activeUdfs.isEmpty()) {
            String combined = String.join("\n", activeUdfs);
            udfSource = Source.newBuilder("js", combined, "udf-library.js")
                    .cached(true)
                    .buildLiteral();
            log.info("Loaded {} user-defined function(s) into JS engine", activeUdfs.size());
        } else {
            udfSource = null;
            log.info("No active user-defined functions found");
        }
    }

    @PreDestroy
    public void destroy() {
        if (engine != null) {
            engine.close();
        }
    }

    @Override
    public String getType() {
        return EndpointType.JS.name();
    }

    @Override
    public ExecuteEndpointResponse execute(
            EndpointEntity endpointEntity,
            Map<String, Object> environment,
            ExecutionContext executionContext) {
        long startTime = System.currentTimeMillis();

        String script = endpointEntity.getBody() != null ? endpointEntity.getBody() : "";
        script = DataUtils.replaceVariables(script, environment);
        script = MockDataUtils.interpolate(script);

        JsExecutionContext jsState = executionContext.getState(JsExecutionContext.class, JsExecutionContext::new);
        String functionName = jsState.getFunctionName();
        List<Object> functionArgs = jsState.getFunctionArgs();

        try (Context context = Context.newBuilder("js")
                .engine(engine)
                .allowHostAccess(HostAccess.ALL)
                .allowHostClassLookup(_ -> true)
                .build()) {

            Value bindings = context.getBindings("js");

            bindings.putMember("env", environment);
            bindings.putMember("setVar", (ProxyExecutable) args -> {
                if (args.length >= 2) {
                    String key = args[0].asString();
                    Object val = safeUnwrap(args[1]);
                    environment.put(key, val);
                }
                return null;
            });

            if (udfSource != null) {
                context.eval(udfSource);
            }

            Value result;

            if (functionName != null && !functionName.isBlank()) {
                context.eval("js", script);

                Value fn = bindings.getMember(functionName);
                if (fn == null || !fn.canExecute()) {
                    throw new IllegalArgumentException("Function not found or not callable: " + functionName);
                }

                Object[] jsArgs = (functionArgs != null) ? functionArgs.toArray() : new Object[0];
                result = fn.execute(jsArgs);
            } else {
                result = context.eval("js", script);
            }

            String rawResponse;
            if (result != null && (result.isHostObject() || result.hasMembers())) {
                try {
                    Value stringify = context.eval("js", "JSON.stringify");
                    rawResponse = stringify.execute(result).asString();
                } catch (Exception _) {
                    rawResponse = result.toString();
                }
            } else {
                rawResponse = String.valueOf(result);
            }

            boolean isSuccess = true;
            Object unwrappedData = safeUnwrap(result);

            if (result != null) {
                if (result.isBoolean()) {
                    isSuccess = result.asBoolean();
                } else if (result.hasMember("success")) {
                    Value successMember = result.getMember("success");
                    if (successMember.isBoolean()) {
                        isSuccess = successMember.asBoolean();
                    }
                }
            }

            return ExecuteEndpointResponse.builder()
                    .statusCode(isSuccess ? 200 : 500)
                    .success(isSuccess)
                    .responseTimeMs(System.currentTimeMillis() - startTime)
                    .data(unwrappedData)
                    .rawResponse(rawResponse)
                    .message(isSuccess ? "JS Execution Successful" : "JS Execution returned false/failed status")
                    .build();

        } catch (Exception e) {
            return ExecuteEndpointResponse.builder()
                    .statusCode(500)
                    .success(false)
                    .responseTimeMs(System.currentTimeMillis() - startTime)
                    .message("JS Execution Error: " + e.getMessage())
                    .build();
        }
    }

    private Object safeUnwrap(Value v) {
        if (v == null || v.isNull())
            return null;
        if (v.isBoolean())
            return v.asBoolean();
        if (v.isNumber())
            return v.as(Number.class);
        if (v.isString())
            return v.asString();

        if (v.hasArrayElements()) {
            List<Object> list = new ArrayList<>();
            for (long i = 0; i < v.getArraySize(); i++) {
                list.add(safeUnwrap(v.getArrayElement(i)));
            }
            return list;
        }

        if (v.hasMembers()) {
            Map<String, Object> map = new HashMap<>();
            for (String key : v.getMemberKeys()) {
                map.put(key, safeUnwrap(v.getMember(key)));
            }
            return map;
        }

        return v.toString();
    }
}
