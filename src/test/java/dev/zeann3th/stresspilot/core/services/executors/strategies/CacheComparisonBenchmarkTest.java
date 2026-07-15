package dev.zeann3th.stresspilot.core.services.executors.strategies;

import dev.zeann3th.stresspilot.core.services.flows.FlowProcessor;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Source;
import org.junit.jupiter.api.Test;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.MapAccessor;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A/B benchmark: measures the exact cost the two production caches remove.
 *   - SpEL:  SPEL_CACHE avoids re-parsing the expression string into an AST each eval.
 *   - Graal: sourceCache avoids rebuilding+re-parsing the JS Source each eval.
 * Cached path == what prod does now. Uncached path == the pre-commit behaviour.
 */
class CacheComparisonBenchmarkTest {

    private static final int WARMUP = 2000;
    private static final int ITERS = 20000;

    // ---------- helpers ----------

    private static double medianUs(long[] samplesNs) {
        long[] copy = samplesNs.clone();
        java.util.Arrays.sort(copy);
        return copy[copy.length / 2] / 1000.0;
    }

    private static void row(String label, double us, double baselineUs) {
        String delta = baselineUs > 0 ? String.format("%.2fx", baselineUs / us) : "-";
        System.out.printf("| %-34s | %12.4f | %8s |%n", label, us, delta);
    }

    // ---------- SpEL ----------

    @Test
    void spelCacheVsUncached() {
        Map<String, Object> vars = new HashMap<>();
        vars.put("token", "abc");
        vars.put("page", 2);
        vars.put("submissionStatus", "ACCEPTED");
        String condition = "['token'] != null && ['page'] > 1 && ['submissionStatus'] == 'ACCEPTED'";

        SpelExpressionParser parser = new SpelExpressionParser();
        Map<String, Expression> localCache = new ConcurrentHashMap<>();

        // Fresh StandardEvaluationContext each iter in BOTH paths, matching prod, so we
        // isolate ONLY the parse cost the cache removes (not context construction).

        // warmup
        for (int i = 0; i < WARMUP; i++) {
            uncachedSpel(parser, condition, vars);
            cachedSpel(parser, localCache, condition, vars);
            FlowProcessor.evaluateCondition(condition, vars);
        }

        long[] un = new long[ITERS];
        long[] ca = new long[ITERS];
        long[] prod = new long[ITERS];
        for (int i = 0; i < ITERS; i++) {
            long s = System.nanoTime();
            uncachedSpel(parser, condition, vars);
            un[i] = System.nanoTime() - s;

            s = System.nanoTime();
            cachedSpel(parser, localCache, condition, vars);
            ca[i] = System.nanoTime() - s;

            s = System.nanoTime();
            FlowProcessor.evaluateCondition(condition, vars);
            prod[i] = System.nanoTime() - s;
        }

        double unUs = medianUs(un);
        double caUs = medianUs(ca);
        double prodUs = medianUs(prod);

        System.out.println("\n=========== SpEL: cached vs uncached (median per eval) ===========");
        System.out.printf("| %-34s | %12s | %8s |%n", "Path", "Median (us)", "Speedup");
        System.out.println("|------------------------------------|--------------|----------|");
        row("Uncached (parse every eval)", unUs, 0);
        row("Cached (local ConcurrentHashMap)", caUs, unUs);
        row("Prod FlowProcessor.evaluateCondition", prodUs, unUs);
        System.out.println("==================================================================");
    }

    private static boolean uncachedSpel(SpelExpressionParser parser, String condition, Map<String, Object> vars) {
        StandardEvaluationContext ctx = new StandardEvaluationContext(vars);
        ctx.addPropertyAccessor(new MapAccessor());
        Expression expr = parser.parseExpression(condition); // re-parse every call
        return Boolean.TRUE.equals(expr.getValue(ctx, Boolean.class));
    }

    private static boolean cachedSpel(SpelExpressionParser parser, Map<String, Expression> cache,
                                      String condition, Map<String, Object> vars) {
        StandardEvaluationContext ctx = new StandardEvaluationContext(vars);
        ctx.addPropertyAccessor(new MapAccessor());
        Expression expr = cache.computeIfAbsent(condition, parser::parseExpression);
        return Boolean.TRUE.equals(expr.getValue(ctx, Boolean.class));
    }

    // ---------- GraalVM JS Source ----------

    @Test
    void graalSourceCacheVsUncached() {
        // Re-evaluable script (no top-level const → safe to eval repeatedly in one context).
        String script = "(function(){ let x = 0; for (let i = 0; i < 8; i++) { x += i * 3; } return x; })()";

        try (Engine engine = Engine.newBuilder("js")
                .option("engine.WarnInterpreterOnly", "false")
                .build();
             Context ctx = Context.newBuilder("js").engine(engine).build()) {

            Source cachedSource = Source.newBuilder("js", script, "endpoint-script.js").buildLiteral();
            Map<String, Source> localCache = new ConcurrentHashMap<>();

            // warmup
            for (int i = 0; i < WARMUP; i++) {
                ctx.eval("js", script);                 // uncached: rebuilds Source internally
                ctx.eval(cachedSource);                 // cached: prebuilt Source
                ctx.eval(localCache.computeIfAbsent(script,
                        c -> Source.newBuilder("js", c, "endpoint-script.js").buildLiteral()));
            }

            long[] un = new long[ITERS];
            long[] ca = new long[ITERS];
            for (int i = 0; i < ITERS; i++) {
                long s = System.nanoTime();
                ctx.eval("js", script);
                un[i] = System.nanoTime() - s;

                s = System.nanoTime();
                ctx.eval(cachedSource);
                ca[i] = System.nanoTime() - s;
            }

            double unUs = medianUs(un);
            double caUs = medianUs(ca);

            System.out.println("\n=========== GraalVM JS Source: cached vs uncached (median per eval) ===========");
            System.out.printf("| %-34s | %12s | %8s |%n", "Path", "Median (us)", "Speedup");
            System.out.println("|------------------------------------|--------------|----------|");
            row("Uncached (eval String → rebuild Source)", unUs, 0);
            row("Cached (prebuilt Source reused)", caUs, unUs);
            System.out.println("===============================================================================");
        }
    }
}
