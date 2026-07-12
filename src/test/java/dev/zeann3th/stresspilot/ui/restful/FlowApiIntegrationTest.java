package dev.zeann3th.stresspilot.ui.restful;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FlowApiIntegrationTest extends AbstractApiIntegrationTest {

    @Test
    void getFlowEndpointsReturnsDistinctEndpointNames() throws Exception {
        ProjectFixture project = createProject("Flow Endpoint List Target");
        long endpointId = createHttpEndpoint(project.id(), "User Login");
        long flowId = createFlow(project.id(), "Endpoint List Flow");

        mockMvc.perform(post("/api/v1/flows/{flowId}/configuration", flowId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [
                                  {"id":"list-start","type":"START","nextIfTrue":"list-login-a"},
                                  {"id":"list-login-a","type":"ENDPOINT","endpointId":%d,"nextIfTrue":"list-login-b"},
                                  {"id":"list-login-b","type":"ENDPOINT","endpointId":%d}
                                ]
                                """.formatted(endpointId, endpointId)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/flows/{flowId}/endpoints", flowId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(endpointId))
                .andExpect(jsonPath("$.data[0].name").value("User Login"));
    }

    @Test
    void createConfigureDetailPatchRunAndDeleteFlow() throws Exception {
        ProjectFixture project = createProject("Flow API Target");
        long endpointId = createHttpEndpoint(project.id(), "Flow Endpoint Target");
        long flowId = createFlow(project.id(), "Flow CRUD Target");

        configureLinearFlow(flowId, endpointId);

        mockMvc.perform(get("/api/v1/flows/{flowId}", flowId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.steps[0].type").value("START"))
                .andExpect(jsonPath("$.data.steps[1].endpointId").value(endpointId))
                .andExpect(jsonPath("$.data.steps[1].preProcessor").value("{\"token\":\"{{ authToken }}\"}"));

        mockMvc.perform(patch("/api/v1/flows/{flowId}", flowId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Flow CRUD Patched","type":"BREAKPOINT","steps":[]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Flow CRUD Patched"))
                .andExpect(jsonPath("$.data.type").value("DEFAULT"));

        MockMultipartFile runRequest = new MockMultipartFile(
                "request",
                "",
                MediaType.APPLICATION_JSON_VALUE,
                """
                        {"threads":1,"totalDuration":1,"rampUpDuration":0,"variables":{"authToken":"token"}}
                        """.getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/api/v1/flows/{flowId}/execute", flowId).file(runRequest))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data").isString());

        mockMvc.perform(delete("/api/v1/flows/{flowId}", flowId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/flows/{flowId}", flowId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errorCode").value("ER0003"));
    }

    @Test
    void configureFlowRejectsMissingAndDuplicateStartButAllowsTerminalStart() throws Exception {
        ProjectFixture project = createProject("Flow Invalid Target");
        long endpointId = createHttpEndpoint(project.id(), "Invalid Flow Endpoint");
        long flowId = createFlow(project.id(), "Invalid Flow");

        mockMvc.perform(post("/api/v1/flows/{flowId}/configuration", flowId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [{"id":"call","type":"ENDPOINT","endpointId":%d}]
                                """.formatted(endpointId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errorCode").value("ER0020"));

        mockMvc.perform(post("/api/v1/flows/{flowId}/configuration", flowId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [
                                  {"id":"start-a","type":"START","nextIfTrue":"call"},
                                  {"id":"start-b","type":"START","nextIfTrue":"call"},
                                  {"id":"call","type":"ENDPOINT","endpointId":%d}
                                ]
                                """.formatted(endpointId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errorCode").value("ER0020"));

        mockMvc.perform(post("/api/v1/flows/{flowId}/configuration", flowId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [{"id":"start","type":"START"}]
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value("start"));
    }

    @Test
    void configureFlowAllowsCyclesAndRejectsUnknownEndpointReferences() throws Exception {
        ProjectFixture project = createProject("Flow Cycle Target");
        long endpointId = createHttpEndpoint(project.id(), "Cycle Endpoint");
        long flowId = createFlow(project.id(), "Cycle Flow");

        mockMvc.perform(post("/api/v1/flows/{flowId}/configuration", flowId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [
                                  {"id":"cycle-start","type":"START","nextIfTrue":"cycle-branch"},
                                  {"id":"cycle-branch","type":"BRANCH","nextIfTrue":"cycle-start","condition":"true"},
                                  {"id":"cycle-call","type":"ENDPOINT","endpointId":%d}
                                ]
                """.formatted(endpointId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value("cycle-start"))
                .andExpect(jsonPath("$.data[1].id").value("cycle-branch"))
                .andExpect(jsonPath("$.data[2].id").value("cycle-call"));

        mockMvc.perform(post("/api/v1/flows/{flowId}/configuration", flowId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [
                                  {"id":"start","type":"START","nextIfTrue":"missing-call"},
                                  {"id":"missing-call","type":"ENDPOINT","endpointId":99999999}
                                ]
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errorCode").value("ER0005"));
    }

    @Test
    void configureFlowRejectsStepIdsOwnedByAnotherFlowAndKeepsOriginalFlowIntact() throws Exception {
        ProjectFixture project = createProject("Flow Duplicate Step Target");
        long endpointId = createHttpEndpoint(project.id(), "Duplicate Step Endpoint");
        long sourceFlowId = createFlow(project.id(), "Source Flow");
        long targetFlowId = createFlow(project.id(), "Target Flow");

        mockMvc.perform(post("/api/v1/flows/{flowId}/configuration", sourceFlowId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [
                                  {"id":"copied-start","type":"START","nextIfTrue":"copied-call"},
                                  {"id":"copied-call","type":"ENDPOINT","endpointId":%d}
                                ]
                                """.formatted(endpointId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));

        mockMvc.perform(post("/api/v1/flows/{flowId}/configuration", targetFlowId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [
                                  {"id":"copied-start","type":"START","nextIfTrue":"copied-call"},
                                  {"id":"copied-call","type":"ENDPOINT","endpointId":%d}
                                ]
                                """.formatted(endpointId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errorCode").value("ER0020"));

        mockMvc.perform(get("/api/v1/flows/{flowId}", sourceFlowId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.steps.length()").value(2))
                .andExpect(jsonPath("$.data.steps[0].id").value("copied-start"))
                .andExpect(jsonPath("$.data.steps[1].id").value("copied-call"));
    }

    @Test
    void configureFlowAllowsSavingExistingStepIdsForTheSameFlow() throws Exception {
        ProjectFixture project = createProject("Flow Same Step Target");
        long endpointId = createHttpEndpoint(project.id(), "Same Step Endpoint");
        long flowId = createFlow(project.id(), "Same Flow");

        mockMvc.perform(post("/api/v1/flows/{flowId}/configuration", flowId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [
                                  {"id":"same-start","type":"START","nextIfTrue":"same-call"},
                                  {"id":"same-call","type":"ENDPOINT","endpointId":%d}
                                ]
                                """.formatted(endpointId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));

        mockMvc.perform(post("/api/v1/flows/{flowId}/configuration", flowId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [
                                  {"id":"same-start","type":"START","nextIfTrue":"same-call"},
                                  {"id":"same-call","type":"ENDPOINT","endpointId":%d,
                                   "preProcessor":{"token":"{{ authToken }}"}}
                                ]
                                """.formatted(endpointId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[1].preProcessor").value("{\"token\":\"{{ authToken }}\"}"));
    }

    @Test
    void runFlowRejectsUnconfiguredFlow() throws Exception {
        ProjectFixture project = createProject("Flow Run Invalid Target");
        long flowId = createFlow(project.id(), "Unconfigured Flow");
        MockMultipartFile runRequest = new MockMultipartFile(
                "request", "", MediaType.APPLICATION_JSON_VALUE,
                """
                        {"threads":1,"totalDuration":1,"rampUpDuration":0}
                        """.getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/v1/flows/{flowId}/execute", flowId).file(runRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errorCode").value("ER0004"));
    }

    @Test
    void runFlowRejectsMissingStopConditions() throws Exception {
        ProjectFixture project = createProject("Flow Stop Condition Target");
        long endpointId = createHttpEndpoint(project.id(), "Stop Condition Endpoint");
        long flowId = createFlow(project.id(), "Stop Condition Flow");
        configureLinearFlow(flowId, endpointId);

        MockMultipartFile runRequest = new MockMultipartFile(
                "request", "", MediaType.APPLICATION_JSON_VALUE,
                """
                        {"threads":1,"totalDuration":null,"loopCount":null,"rampUpDuration":0}
                        """.getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/v1/flows/{flowId}/execute", flowId).file(runRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errorCode").value("ER0001"));
    }

    @Test
    void runFlowAcceptsLoopOnlyStopCondition() throws Exception {
        ProjectFixture project = createProject("Flow Loop Only Target");
        long endpointId = createHttpEndpoint(project.id(), "Loop Only Endpoint");
        long flowId = createFlow(project.id(), "Loop Only Flow");
        configureLinearFlow(flowId, endpointId);

        MockMultipartFile runRequest = new MockMultipartFile(
                "request", "", MediaType.APPLICATION_JSON_VALUE,
                """
                        {"threads":1,"totalDuration":null,"loopCount":1,"rampUpDuration":0}
                        """.getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/v1/flows/{flowId}/execute", flowId).file(runRequest))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data").isString());
    }
}
