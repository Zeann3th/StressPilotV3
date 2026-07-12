package dev.zeann3th.stresspilot.core.services.executors.strategies;

import dev.zeann3th.stresspilot.core.domain.entities.EndpointEntity;
import dev.zeann3th.stresspilot.core.services.executors.context.BaseExecutionContext;
import dev.zeann3th.stresspilot.core.services.flows.FlowProcessor;
import dev.zeann3th.stresspilot.core.services.functions.FunctionService;
import dev.zeann3th.stresspilot.core.utils.DataUtils;
import dev.zeann3th.stresspilot.core.utils.MockDataUtils;
import org.junit.jupiter.api.Test;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PerformanceProfilerTest {

    private final JsonMapper jsonMapper = new JsonMapper();

    @Test
    void runProfilingBenchmark() throws Exception {
        System.out.println("=================================================");
        System.out.println("   STRESSPILOT CLIENT-SIDE CPU PROFILING TEST    ");
        System.out.println("=================================================");

        // Setup mock environment variables
        Map<String, Object> environment = new HashMap<>();
        environment.put("token", "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.dummyTokenValue");
        environment.put("problemId", "654c2a123f4b5c67890def12");
        environment.put("page", 2);

        // Setup test datasets
        String headersJson = "{\"Content-Type\": \"application/json\", \"Authorization\": \"Bearer {{token}}\", \"X-Request-ID\": \"@{uuid}@\"}";
        String requestBodyTemplate = "{\n" +
                "  \"username\": \"@{faker(Name.username)}@\",\n" +
                "  \"email\": \"@{faker(Internet.emailAddress)}@\",\n" +
                "  \"age\": @{faker(Number.number_between '18','80')}@,\n" +
                "  \"active\": @{pick(true,false)}@,\n" +
                "  \"problemId\": \"{{problemId}}\"\n" +
                "}";

        // Simulating a typical 5KB JSON Response containing problem lists from LeetBase BE
        String responseJson = "{\n" +
                "  \"success\": true,\n" +
                "  \"data\": {\n" +
                "    \"totalItems\": 120,\n" +
                "    \"page\": 2,\n" +
                "    \"items\": [\n" +
                "      { \"id\": \"1\", \"title\": \"Two Sum\", \"difficulty\": \"EASY\", \"solved\": true },\n" +
                "      { \"id\": \"2\", \"title\": \"Add Two Numbers\", \"difficulty\": \"MEDIUM\", \"solved\": false },\n" +
                "      { \"id\": \"3\", \"title\": \"Longest Substring\", \"difficulty\": \"MEDIUM\", \"solved\": true },\n" +
                "      { \"id\": \"4\", \"title\": \"Median of Two Sorted Arrays\", \"difficulty\": \"HARD\", \"solved\": false },\n" +
                "      { \"id\": \"5\", \"title\": \"Longest Palindromic Substring\", \"difficulty\": \"MEDIUM\", \"solved\": false }\n" +
                "    ]\n" +
                "  },\n" +
                "  \"timestamp\": 1720778400000\n" +
                "}";

        String spelCondition = "['token'] != null && ['page'] > 1";

        int warmupIterations = 500;
        int benchmarkIterations = 1000;

        // Warmup JVM
        for (int i = 0; i < warmupIterations; i++) {
            jsonMapper.readValue(headersJson, new TypeReference<Map<String, String>>() {});
            DataUtils.replaceVariables("Bearer {{token}}", environment);
            MockDataUtils.interpolate(requestBodyTemplate);
            jsonMapper.readValue(responseJson, new TypeReference<Map<String, Object>>() {});
            FlowProcessor.evaluateCondition(spelCondition, environment);
        }

        // Benchmarking Phase
        long totalHeaderParseTime = 0;
        long totalVarReplacementTime = 0;
        long totalMockInterpolationTime = 0;
        long totalResponseParseTime = 0;
        long totalSpelEvaluationTime = 0;

        for (int i = 0; i < benchmarkIterations; i++) {
            // 1. Header parsing (Jackson readValue)
            long start = System.nanoTime();
            Map<String, String> parsedHeaders = jsonMapper.readValue(headersJson, new TypeReference<>() {});
            totalHeaderParseTime += (System.nanoTime() - start);

            // 2. Variable replacement in URL/Headers
            start = System.nanoTime();
            String replacedToken = DataUtils.replaceVariables("Bearer {{token}}", environment);
            totalVarReplacementTime += (System.nanoTime() - start);

            // 3. Mock Data generation / Datafaker interpolation
            start = System.nanoTime();
            String body = MockDataUtils.interpolate(requestBodyTemplate);
            totalMockInterpolationTime += (System.nanoTime() - start);

            // 4. Response parsing (Jackson readValue of response body)
            start = System.nanoTime();
            Map<String, Object> responseData = jsonMapper.readValue(responseJson, new TypeReference<>() {});
            totalResponseParseTime += (System.nanoTime() - start);

            // 5. SpEL Expression Evaluation
            start = System.nanoTime();
            FlowProcessor.evaluateCondition(spelCondition, environment);
            totalSpelEvaluationTime += (System.nanoTime() - start);
        }

        // Print Results
        double avgHeaderParse = totalHeaderParseTime / (benchmarkIterations * 1000.0);
        double avgVarReplace = totalVarReplacementTime / (benchmarkIterations * 1000.0);
        double avgMockInterp = totalMockInterpolationTime / (benchmarkIterations * 1000.0);
        double avgResponseParse = totalResponseParseTime / (benchmarkIterations * 1000.0);
        double avgSpelEval = totalSpelEvaluationTime / (benchmarkIterations * 1000.0);

        double totalAvg = avgHeaderParse + avgVarReplace + avgMockInterp + avgResponseParse + avgSpelEval;

        System.out.printf("| %-28s | %-12s | %-10s |\n", "Operation Type", "Avg Time (us)", "Percentage");
        System.out.println("|------------------------------|--------------|------------|");
        System.out.printf("| %-28s | %10.2f   | %8.2f%%  |\n", "1. Header JSON Parse", avgHeaderParse, (avgHeaderParse/totalAvg)*100);
        System.out.printf("| %-28s | %10.2f   | %8.2f%%  |\n", "2. Variable Replacements", avgVarReplace, (avgVarReplace/totalAvg)*100);
        System.out.printf("| %-28s | %10.2f   | %8.2f%%  |\n", "3. Faker/Mock Interpolation", avgMockInterp, (avgMockInterp/totalAvg)*100);
        System.out.printf("| %-28s | %10.2f   | %8.2f%%  |\n", "4. Response JSON Parse", avgResponseParse, (avgResponseParse/totalAvg)*100);
        System.out.printf("| %-28s | %10.2f   | %8.2f%%  |\n", "5. SpEL Condition Eval", avgSpelEval, (avgSpelEval/totalAvg)*100);
        System.out.println("|------------------------------|--------------|------------|");
        System.out.printf("| %-28s | %10.2f   | %8.2f%%  |\n", "Total Client Overhead", totalAvg, 100.0);
        System.out.println("=================================================");
        System.out.printf("Max theoretically possible RPS per single thread: %.2f RPS\n", 1000000.0 / totalAvg);
        System.out.println("=================================================");
    }

    @Test
    void profileRealScenarioFlow() throws Exception {
        System.out.println("=================================================");
        System.out.println("   REAL LEETBASE SCENARIO PROFILE BENCHMARK     ");
        System.out.println("=================================================");

        // Mock JS Engine dependencies
        FunctionService functionService = mock(FunctionService.class);
        when(functionService.getAllFunctions()).thenReturn(List.of());
        JsEndpointExecutor jsExecutor = new JsEndpointExecutor(functionService);
        jsExecutor.init();

        // Real script endpoints from user kịch bản
        EndpointEntity initJsEndpoint = EndpointEntity.builder()
                .id(288L)
                .name("Init randomized seeded user and problem pick")
                .type("JS")
                .body("const page = Math.floor(Math.random() * 10) + 1; setVar('problemPage', page); setVar('language', 'javascript');")
                .build();

        EndpointEntity buildJsEndpoint = EndpointEntity.builder()
                .id(295L)
                .name("Build accepted or wrong JavaScript solution")
                .type("JS")
                .body("setVar('solutionCode', 'function solve() { return 42; }');")
                .build();

        // Environment Map simulation
        Map<String, Object> variables = new HashMap<>();
        variables.put("baseUrl", "http://leetbase-api.zeann3th.com");

        BaseExecutionContext executionContext = new BaseExecutionContext();

        // JSON Response payload simulations
        String loginResponse = "{\"accessToken\":\"eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9\",\"csrfToken\":\"csrfTokenVal\"}";
        String browseResponse = "{\"data\":[{\"_id\":\"654c2a123f4b5c67890def12\",\"title\":\"Two Sum\",\"difficulty\":\"EASY\",\"tags\":[\"array\"]}]}";
        String submitResponse = "{\"_id\":\"sub-12345678\",\"status\":\"ACCEPTED\",\"runtime\":12}";

        int iterations = 1000;

        // Warmup
        for (int i = 0; i < 200; i++) {
            jsExecutor.execute(initJsEndpoint, variables, executionContext);
            jsExecutor.execute(buildJsEndpoint, variables, executionContext);
            jsonMapper.readValue(loginResponse, new TypeReference<Map<String, Object>>() {});
            jsonMapper.readValue(browseResponse, new TypeReference<Map<String, Object>>() {});
            jsonMapper.readValue(submitResponse, new TypeReference<Map<String, Object>>() {});
            FlowProcessor.evaluateCondition("['submissionStatus'] == 'ACCEPTED'", variables);
        }

        // Benchmark Measurements
        long totalInitJsTime = 0;
        long totalBuildJsTime = 0;
        long totalRequestProcessingTime = 0;
        long totalResponseParsingTime = 0;
        long totalSpelBranchTime = 0;

        for (int i = 0; i < iterations; i++) {
            // 1. Step 2 (Init JS Script) execution
            long start = System.nanoTime();
            jsExecutor.execute(initJsEndpoint, variables, executionContext);
            totalInitJsTime += (System.nanoTime() - start);

            // 2. Step 3 & 4 (Request generation: Variable replacement in URLs/Headers)
            start = System.nanoTime();
            String loginUrl = DataUtils.replaceVariables("{{ baseUrl }}/v1/auth/login", variables);
            String browseUrl = DataUtils.replaceVariables("{{ baseUrl }}/v1/problems?limit=1&page={{ problemPage }}", variables);
            totalRequestProcessingTime += (System.nanoTime() - start);

            // 3. Step 8 (Build JS Script) execution
            start = System.nanoTime();
            jsExecutor.execute(buildJsEndpoint, variables, executionContext);
            totalBuildJsTime += (System.nanoTime() - start);

            // 4. Response Data Parsing and Variable Extractions (equivalent to Jackson readValue + extracts mapping)
            start = System.nanoTime();
            Map<String, Object> parsedLogin = jsonMapper.readValue(loginResponse, new TypeReference<>() {});
            variables.put("accessToken", parsedLogin.get("accessToken"));
            variables.put("csrfToken", parsedLogin.get("csrfToken"));

            Map<String, Object> parsedBrowse = jsonMapper.readValue(browseResponse, new TypeReference<>() {});
            // Data extraction path data.0._id
            Map<String, Object> dataObj = (Map<String, Object>) ((List<?>) parsedBrowse.get("data")).get(0);
            variables.put("problemId", dataObj.get("_id"));
            variables.put("problemTitle", dataObj.get("title"));

            Map<String, Object> parsedSubmit = jsonMapper.readValue(submitResponse, new TypeReference<>() {});
            variables.put("submissionStatus", parsedSubmit.get("status"));
            totalResponseParsingTime += (System.nanoTime() - start);

            // 5. SpEL Branch Node evaluation
            start = System.nanoTime();
            FlowProcessor.evaluateCondition("['submissionStatus'] == 'ACCEPTED'", variables);
            totalSpelBranchTime += (System.nanoTime() - start);
        }

        // Print Results
        double avgInitJs = totalInitJsTime / (iterations * 1000.0);
        double avgBuildJs = totalBuildJsTime / (iterations * 1000.0);
        double avgRequestProcess = totalRequestProcessingTime / (iterations * 1000.0);
        double avgResponseParse = totalResponseParsingTime / (iterations * 1000.0);
        double avgSpelBranch = totalSpelBranchTime / (iterations * 1000.0);

        double totalAvg = avgInitJs + avgBuildJs + avgRequestProcess + avgResponseParse + avgSpelBranch;

        System.out.printf("| %-28s | %-12s | %-10s |\n", "Scenario Phase", "Avg Time (us)", "Percentage");
        System.out.println("|------------------------------|--------------|------------|");
        System.out.printf("| %-28s | %10.2f   | %8.2f%%  |\n", "1. Init JS Script (Graal)", avgInitJs, (avgInitJs/totalAvg)*100);
        System.out.printf("| %-28s | %10.2f   | %8.2f%%  |\n", "2. Request URL Interpolations", avgRequestProcess, (avgRequestProcess/totalAvg)*100);
        System.out.printf("| %-28s | %10.2f   | %8.2f%%  |\n", "3. Build JS Solution (Graal)", avgBuildJs, (avgBuildJs/totalAvg)*100);
        System.out.printf("| %-28s | %10.2f   | %8.2f%%  |\n", "4. Response JSON parse/extract", avgResponseParse, (avgResponseParse/totalAvg)*100);
        System.out.printf("| %-28s | %10.2f   | %8.2f%%  |\n", "5. Branch SpEL evaluation", avgSpelBranch, (avgSpelBranch/totalAvg)*100);
        System.out.println("|------------------------------|--------------|------------|");
        System.out.printf("| %-28s | %10.2f   | %8.2f%%  |\n", "Total Scenario Execution", totalAvg, 100.0);
        System.out.println("=================================================");
        System.out.printf("Max theoretically possible RPS per single thread: %.2f RPS\n", 1000000.0 / totalAvg);
        System.out.println("=================================================");

        jsExecutor.destroy();
    }

    @Test
    void profilePureHttpCall() throws Exception {
        System.out.println("=================================================");
        System.out.println("   PURE HTTP GET CALL BENCHMARK (NO JS / NO FAKER) ");
        System.out.println("=================================================");

        Map<String, Object> environment = new HashMap<>();
        environment.put("baseUrl", "http://leetbase-api.zeann3th.com");
        environment.put("statusCode", 200);

        String headersJson = "{\"Content-Type\": \"application/json\"}";
        String responseJson = "{\"success\":true}";
        String spelCondition = "['statusCode'] == 200";

        int iterations = 1000;

        // Warmup
        for (int i = 0; i < 200; i++) {
            DataUtils.replaceVariables("{{ baseUrl }}/v1/problems", environment);
            jsonMapper.readValue(headersJson, new TypeReference<Map<String, String>>() {});
            jsonMapper.readValue(responseJson, new TypeReference<Map<String, Object>>() {});
            FlowProcessor.evaluateCondition(spelCondition, environment);
        }

        long totalUrlInterpTime = 0;
        long totalHeaderParseTime = 0;
        long totalResponseParseTime = 0;
        long totalSpelEvalTime = 0;

        for (int i = 0; i < iterations; i++) {
            // 1. URL Interpolation
            long start = System.nanoTime();
            String url = DataUtils.replaceVariables("{{ baseUrl }}/v1/problems", environment);
            totalUrlInterpTime += (System.nanoTime() - start);

            // 2. Parse Headers
            start = System.nanoTime();
            Map<String, String> headers = jsonMapper.readValue(headersJson, new TypeReference<>() {});
            totalHeaderParseTime += (System.nanoTime() - start);

            // 3. Parse Tiny Response
            start = System.nanoTime();
            Map<String, Object> responseData = jsonMapper.readValue(responseJson, new TypeReference<>() {});
            totalResponseParseTime += (System.nanoTime() - start);

            // 4. Simple SpEL Eval
            start = System.nanoTime();
            FlowProcessor.evaluateCondition(spelCondition, environment);
            totalSpelEvalTime += (System.nanoTime() - start);
        }

        double avgUrlInterp = totalUrlInterpTime / (iterations * 1000.0);
        double avgHeaderParse = totalHeaderParseTime / (iterations * 1000.0);
        double avgResponseParse = totalResponseParseTime / (iterations * 1000.0);
        double avgSpelEval = totalSpelEvalTime / (iterations * 1000.0);

        double totalAvg = avgUrlInterp + avgHeaderParse + avgResponseParse + avgSpelEval;

        System.out.printf("| %-28s | %-12s | %-10s |\n", "Operation Type", "Avg Time (us)", "Percentage");
        System.out.println("|------------------------------|--------------|------------|");
        System.out.printf("| %-28s | %10.2f   | %8.2f%%  |\n", "1. URL Interpolation", avgUrlInterp, (avgUrlInterp/totalAvg)*100);
        System.out.printf("| %-28s | %10.2f   | %8.2f%%  |\n", "2. Header JSON Parse", avgHeaderParse, (avgHeaderParse/totalAvg)*100);
        System.out.printf("| %-28s | %10.2f   | %8.2f%%  |\n", "3. Response JSON Parse (Tiny)", avgResponseParse, (avgResponseParse/totalAvg)*100);
        System.out.printf("| %-28s | %10.2f   | %8.2f%%  |\n", "4. Simple SpEL Eval", avgSpelEval, (avgSpelEval/totalAvg)*100);
        System.out.println("|------------------------------|--------------|------------|");
        System.out.printf("| %-28s | %10.2f   | %8.2f%%  |\n", "Total Client Overhead", totalAvg, 100.0);
        System.out.println("=================================================");
        System.out.printf("Max theoretically possible RPS per single thread: %.2f RPS\n", 1000000.0 / totalAvg);
        System.out.println("=================================================");
    }
}

