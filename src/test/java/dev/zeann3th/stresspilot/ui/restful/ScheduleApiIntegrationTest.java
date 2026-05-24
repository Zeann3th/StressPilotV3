package dev.zeann3th.stresspilot.ui.restful;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ScheduleApiIntegrationTest extends AbstractApiIntegrationTest {

    @Test
    void createListDetailPatchAndDeleteDisabledSchedule() throws Exception {
        ProjectFixture project = createProject("Schedule API Target");
        long flowId = createFlow(project.id(), "Schedule Flow Target");

        MvcResult created = mockMvc.perform(post("/api/v1/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"flowId":%d,"quartzExpr":"0 0 0 * * ?","threads":2,"duration":60,"rampUp":5,"enabled":false}
                                """.formatted(flowId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.flowId").value(flowId))
                .andReturn();
        long scheduleId = readLong(created, "$.data.id");

        mockMvc.perform(get("/api/v1/schedules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").isNumber());

        mockMvc.perform(get("/api/v1/schedules/{scheduleId}", scheduleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(false));

        mockMvc.perform(patch("/api/v1/schedules/{scheduleId}", scheduleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"threads":4,"duration":120,"rampUp":10}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.threads").value(4))
                .andExpect(jsonPath("$.data.duration").value(120))
                .andExpect(jsonPath("$.data.rampUp").value(10));

        mockMvc.perform(delete("/api/v1/schedules/{scheduleId}", scheduleId))
                .andExpect(status().isNoContent());
    }

    @Test
    void scheduleRejectsMissingFlowAndMissingSchedule() throws Exception {
        mockMvc.perform(post("/api/v1/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"flowId":99999994,"quartzExpr":"0 0 0 * * ?","enabled":false}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errorCode").value("ER0003"));

        mockMvc.perform(patch("/api/v1/schedules/{scheduleId}", 9_999_995L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"enabled":false}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errorCode").value("ER0003"));
    }

    @Test
    void enabledScheduleActuallyStartsAndCompletesFlowRuns() throws Exception {
        ProjectFixture project = createProject("Schedule Execution Target");
        MvcResult endpoint = mockMvc.perform(post("/api/v1/endpoints")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId":%d,
                                  "name":"Scheduled JS Endpoint",
                                  "type":"JS",
                                  "body":"true"
                                }
                                """.formatted(project.id())))
                .andExpect(status().isOk())
                .andReturn();
        long endpointId = readLong(endpoint, "$.data.id");
        long flowId = createFlow(project.id(), "Scheduled Executable Flow");
        configureLinearFlow(flowId, endpointId);

        MvcResult createdSchedule = mockMvc.perform(post("/api/v1/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"flowId":%d,"quartzExpr":"0/1 * * * * ?","threads":1,"duration":1,"rampUp":0,"enabled":true}
                                """.formatted(flowId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.enabled").value(true))
                .andReturn();
        long scheduleId = readLong(createdSchedule, "$.data.id");

        try {
            String completedRunId = waitForCompletedRun(flowId);
            mockMvc.perform(get("/api/v1/runs/{runId}", completedRunId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.flowId").value(flowId))
                    .andExpect(jsonPath("$.data.status").value("COMPLETED"));
        } finally {
            mockMvc.perform(delete("/api/v1/schedules/{scheduleId}", scheduleId))
                    .andExpect(status().isNoContent());
        }
    }

    @Test
    void scheduleRejectsInvalidCronBeforePersistingRunnableJob() throws Exception {
        ProjectFixture project = createProject("Schedule Invalid Cron Target");
        long flowId = createFlow(project.id(), "Invalid Cron Flow");

        mockMvc.perform(post("/api/v1/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"flowId":%d,"quartzExpr":"not-a-cron","threads":1,"duration":1,"rampUp":0,"enabled":true}
                                """.formatted(flowId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errorCode").value("ER0026"));
    }

    private String waitForCompletedRun(long flowId) throws Exception {
        AtomicReference<String> completedRunId = new AtomicReference<>();
        await()
                .atMost(Duration.ofSeconds(8))
                .pollInterval(Duration.ofMillis(250))
                .untilAsserted(() -> {
            MvcResult result = mockMvc.perform(get("/api/v1/runs")
                            .param("flowId", String.valueOf(flowId)))
                    .andExpect(status().isOk())
                    .andReturn();
            String body = result.getResponse().getContentAsString();
            List<String> completed = JsonPath.read(body, "$.data[?(@.status == 'COMPLETED')].id");
            assertThat(completed)
                    .as("completed scheduled run in response body: %s", body)
                    .isNotEmpty();
            completedRunId.set(completed.getFirst());
        });
        return completedRunId.get();
    }
}
