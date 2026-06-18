package dev.zeann3th.stresspilot.core.utils.report;

import dev.zeann3th.stresspilot.core.domain.commands.run.EndpointStats;
import dev.zeann3th.stresspilot.core.domain.commands.run.RequestLog;
import dev.zeann3th.stresspilot.core.domain.commands.run.RunReport;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.spel.SpelEvaluationException;
import org.springframework.expression.spel.SpelMessage;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.util.List;

/**
 * Factory that creates SpEL evaluation contexts safe for use with user-supplied expressions.
 *
 * <p>The standard {@link StandardEvaluationContext} allows the {@code T()} operator which can
 * reference arbitrary Java types, enabling OS command execution via
 * {@code T(java.lang.Runtime).getRuntime().exec(...)}. This factory disables the
 * {@code TypeLocator} and removes all {@code ConstructorResolver}s so that such expressions
 * throw a {@link SpelEvaluationException} instead of executing.
 *
 * <p>Instance-method calls (e.g. {@code #report.successRate}) continue to work because the
 * default {@code MethodResolver} is retained.
 */
public final class SafeSpelContextFactory {

    private SafeSpelContextFactory() {
    }

    /**
     * Build a safe context with {@code #report}, {@code #logs}, and {@code #stats} variables.
     * Used by {@code StatElementRenderer} and {@code PieChartElementRenderer}.
     */
    public static EvaluationContext create(RunReport report, List<RequestLog> logs, List<EndpointStats> stats) {
        StandardEvaluationContext ctx = buildSafeBase();
        ctx.setVariable("report", report);
        ctx.setVariable("logs", logs);
        ctx.setVariable("stats", stats);
        return ctx;
    }

    /**
     * Build a safe context with a single {@code #bucket} variable.
     * Used by {@code LineChartElementRenderer}.
     */
    public static EvaluationContext createForBucket(ReportTimeBucket bucket) {
        StandardEvaluationContext ctx = buildSafeBase();
        ctx.setVariable("bucket", bucket);
        return ctx;
    }

    /**
     * Build a safe context with a single {@code #stat} variable.
     * Used by {@code BarChartElementRenderer}.
     */
    public static EvaluationContext createForStat(EndpointStats stat) {
        StandardEvaluationContext ctx = buildSafeBase();
        ctx.setVariable("stat", stat);
        return ctx;
    }

    /**
     * Creates a {@link StandardEvaluationContext} with the TypeLocator disabled and all
     * ConstructorResolvers removed, preventing {@code T()} and {@code new} expressions.
     */
    private static StandardEvaluationContext buildSafeBase() {
        StandardEvaluationContext ctx = new StandardEvaluationContext();
        // Block T(SomeClass) operator — prevents static method/field access and class instantiation
        ctx.setTypeLocator(typeName -> {
            throw new SpelEvaluationException(SpelMessage.TYPE_NOT_FOUND, typeName);
        });
        // Remove constructor resolvers — prevents new SomeClass(...) expressions
        ctx.setConstructorResolvers(List.of());
        return ctx;
    }
}
