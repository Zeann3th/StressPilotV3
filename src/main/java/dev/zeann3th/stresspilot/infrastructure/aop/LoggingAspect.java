package dev.zeann3th.stresspilot.infrastructure.aop;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Aspect
@Component
public class LoggingAspect {
    private final JsonMapper jsonMapper = new JsonMapper();

    @Pointcut("@within(org.springframework.web.bind.annotation.RestController) || " +
            "@within(org.springframework.grpc.server.service.GrpcService)")
    public void uiLayer() {}

    @Around("uiLayer()")
    public Object logInOut(ProceedingJoinPoint joinPoint) throws Throwable {
        String methodName = joinPoint.getSignature().toShortString();
        Object[] args = joinPoint.getArgs();

        log.info("==> [START] {}: args={}", methodName, safeJson(args));

        long start = System.currentTimeMillis();
        try {
            Object result = joinPoint.proceed();
            long duration = System.currentTimeMillis() - start;

            log.info("<== [END] {} ({}ms): response={}", methodName, duration, safeJson(result));
            return result;
        } catch (Throwable e) {
            log.error("!!! [ERROR] {} : {}", methodName, e.getMessage());
            throw e;
        }
    }

    private String safeJson(Object obj) {
        if (obj == null) return "null";
        try {
            return jsonMapper.writeValueAsString(obj);
        } catch (Exception _) {
            return String.valueOf(obj);
        }
    }
}