package dev.zeann3th.stresspilot.core.services.executors.strategies;

import dev.zeann3th.stresspilot.core.domain.entities.EndpointEntity;
import dev.zeann3th.stresspilot.core.domain.enums.EndpointType;
import dev.zeann3th.stresspilot.core.services.executors.context.BaseExecutionContext;
import dev.zeann3th.stresspilot.core.services.executors.context.JsExecutionContext;
import dev.zeann3th.stresspilot.core.services.executors.context.HttpExecutionContext;
import dev.zeann3th.stresspilot.core.services.functions.FunctionService;
import org.junit.jupiter.api.Test;
import org.graalvm.polyglot.Context;
import okhttp3.OkHttpClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PerformanceBenchmarkTest {

    @Test
    void benchmarkJsExecutorPerformanceAndContextCaching() {
        FunctionService functionService = mock(FunctionService.class);
        when(functionService.getAllFunctions()).thenReturn(List.of());
        
        JsEndpointExecutor executor = new JsEndpointExecutor(functionService);
        executor.init();

        EndpointEntity endpoint = EndpointEntity.builder()
                .type(EndpointType.JS.name())
                .body("""
                        const a = 10;
                        const b = 20;
                        return { success: a + b === 30 };
                        """)
                .build();

        int iterations = 1000;
        
        // Mode A: Old way (No context caching - new execution context every time)
        long startOld = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            var context = new BaseExecutionContext();
            executor.execute(endpoint, new HashMap<>(), context);
            context.clear(); 
        }
        long durationOld = System.nanoTime() - startOld;

        // Mode B: New way (Context caching - reuse the same execution context)
        long startNew = System.nanoTime();
        var cachedContext = new BaseExecutionContext();
        for (int i = 0; i < iterations; i++) {
            executor.execute(endpoint, new HashMap<>(), cachedContext);
        }
        
        JsExecutionContext jsState = cachedContext.getState(JsExecutionContext.class, JsExecutionContext::new);
        Context graalContext = jsState.getGraalContext();
        assertThat(graalContext).isNotNull();

        long durationNew = System.nanoTime() - startNew;
        
        cachedContext.clear();
        
        // Verify that clearing the execution context closes the GraalVM Context (Memory Leak check)
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> {
            graalContext.eval("js", "true");
        });

        System.out.println("=== JS EXECUTOR PERFORMANCE BENCHMARK ===");
        System.out.println("Mode A (Old - No cache): " + (durationOld / 1_000_000.0) + " ms");
        System.out.println("Mode B (New - Cached):   " + (durationNew / 1_000_000.0) + " ms");
        double speedup = (double) durationOld / durationNew;
        System.out.println("Speedup factor:          " + String.format("%.2f", speedup) + "x");
        System.out.println("=========================================");

        assertThat(durationNew).isLessThan(durationOld);
        
        executor.destroy();
    }

    @Test
    void benchmarkHttpExecutorPerformanceAndCookieIsolation() {
        OkHttpClient baseClient = new OkHttpClient();
        
        // 1. HttpClient Reuse Verification (Same VU should get the same client instance)
        BaseExecutionContext vu1Context = new BaseExecutionContext();
        HttpExecutionContext httpContext1 = vu1Context.getState(HttpExecutionContext.class, HttpExecutionContext::new);
        
        OkHttpClient client1a = httpContext1.getHttpClient();
        if (client1a == null) {
            client1a = baseClient.newBuilder().cookieJar(httpContext1).build();
            httpContext1.setHttpClient(client1a);
        }
        
        OkHttpClient client1b = httpContext1.getHttpClient();
        assertThat(client1b).isSameAs(client1a); 

        // 2. Cookie Isolation Verification (Different VUs must NOT share cookies or clients)
        BaseExecutionContext vu2Context = new BaseExecutionContext();
        HttpExecutionContext httpContext2 = vu2Context.getState(HttpExecutionContext.class, HttpExecutionContext::new);
        
        OkHttpClient client2 = httpContext2.getHttpClient();
        if (client2 == null) {
            client2 = baseClient.newBuilder().cookieJar(httpContext2).build();
            httpContext2.setHttpClient(client2);
        }
        
        assertThat(client2).isNotSameAs(client1a); 
        
        // Add a cookie to VU1
        okhttp3.HttpUrl url = okhttp3.HttpUrl.parse("https://leetbase-api.zeann3th.com");
        okhttp3.Cookie cookie = new okhttp3.Cookie.Builder()
                .name("session")
                .value("vu1-secret-token")
                .domain("leetbase-api.zeann3th.com")
                .build();
        httpContext1.saveFromResponse(url, List.of(cookie));
        
        // Verify VU1 has the cookie
        assertThat(httpContext1.loadForRequest(url)).hasSize(1);
        
        // Verify VU2 has NO cookies (Strict Isolation)
        assertThat(httpContext2.loadForRequest(url)).isEmpty();
        
        // 3. Performance Benchmark (Instantiating and rebuilding clients 50,000 times vs Caching)
        int iterations = 50000;
        
        // Mode A: Rebuilding client every request (Old way)
        long startOld = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            OkHttpClient tempClient = baseClient.newBuilder()
                    .cookieJar(httpContext1)
                    .build();
            tempClient.dispatcher();
        }
        long durationOld = System.nanoTime() - startOld;
        
        // Mode B: Reusing cached client (New way)
        long startNew = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            OkHttpClient tempClient = httpContext1.getHttpClient();
            tempClient.dispatcher();
        }
        long durationNew = System.nanoTime() - startNew;
        
        System.out.println("=== HTTP EXECUTOR PERFORMANCE BENCHMARK ===");
        System.out.println("Mode A (Old - No cache): " + (durationOld / 1_000_000.0) + " ms");
        System.out.println("Mode B (New - Cached):   " + (durationNew / 1_000_000.0) + " ms");
        double speedup = (double) durationOld / durationNew;
        System.out.println("Speedup factor:          " + String.format("%.2f", speedup) + "x");
        System.out.println("=========================================");
        
        assertThat(durationNew).isLessThan(durationOld);
    }
}
