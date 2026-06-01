package dev.zeann3th.stresspilot.ui.restful;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProjectApiIntegrationTest extends AbstractApiIntegrationTest {

    @Test
    void createListDetailUpdateExportAndDeleteProject() throws Exception {
        ProjectFixture project = createProject("Project API Target");

        mockMvc.perform(get("/api/v1/projects")
                        .param("name", "project api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(project.id()))
                .andExpect(jsonPath("$.data.content[0].environmentId").value(project.environmentId()))
                .andExpect(jsonPath("$.data.content[0].activeEnvironmentId").value(project.environmentId()));

        mockMvc.perform(get("/api/v1/projects/{projectId}", project.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Project API Target"))
                .andExpect(jsonPath("$.data.environmentId").value(project.environmentId()))
                .andExpect(jsonPath("$.data.activeEnvironmentId").value(project.environmentId()));

        mockMvc.perform(patch("/api/v1/projects/{projectId}", project.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Project API Target Updated","description":"patched"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Project API Target Updated"))
                .andExpect(jsonPath("$.data.description").value("patched"));

        mockMvc.perform(get("/api/v1/projects/{projectId}/export", project.id()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("project_" + project.id() + ".yaml")))
                .andExpect(content().string(containsString("Project API Target Updated")));

        mockMvc.perform(delete("/api/v1/projects/{projectId}", project.id()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/projects/{projectId}", project.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errorCode").value("ER0002"));
    }

    @Test
    void invalidProjectRequestsReturnWrappedErrors() throws Exception {
        mockMvc.perform(post("/api/v1/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":""}
                                """))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errorCode").value("ER9999"));

        mockMvc.perform(delete("/api/v1/projects/{projectId}", 9_999_999L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errorCode").value("ER0002"));

        MockMultipartFile emptyFile = new MockMultipartFile(
                "file", "empty.yaml", "application/x-yaml", new byte[0]);
        mockMvc.perform(multipart("/api/v1/projects/import").file(emptyFile))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errorCode").value("ER0011"));
    }

    @Test
    void createListAndSwitchProjectEnvironments() throws Exception {
        ProjectFixture project = createProject("Project Environments Target");

        mockMvc.perform(get("/api/v1/projects/{projectId}/environments", project.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(project.environmentId()))
                .andExpect(jsonPath("$.data[0].name").value("Default"));

        MvcResult created = mockMvc.perform(post("/api/v1/projects/{projectId}/environments", project.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Staging"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Staging"))
                .andReturn();
        long stagingEnvId = readLong(created, "$.data.id");

        mockMvc.perform(patch("/api/v1/projects/{projectId}/active-environment", project.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"environmentId":%d}
                                """.formatted(stagingEnvId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.activeEnvironmentId").value(stagingEnvId));

        mockMvc.perform(get("/api/v1/projects/{projectId}", project.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.environmentId").value(stagingEnvId))
                .andExpect(jsonPath("$.data.activeEnvironmentId").value(stagingEnvId));
    }

    @Test
    void importProjectSupportsMultipleEnvironmentsFlowTypesAndSkipsInvalidFlows() throws Exception {
        MockMultipartFile spec = new MockMultipartFile(
                "file",
                "multi-env.yaml",
                "application/x-yaml",
                """
                        stresspilot:
                          project:
                            name: Imported Multi Env
                            description: imported
                          environments:
                            - name: Local
                              active: true
                              variables:
                                - name: baseUrl
                                  value: http://localhost:8080
                            - name: Staging
                              variables:
                                - name: baseUrl
                                  value: https://staging.test
                          endpoints:
                            - id: health
                              name: Health
                              type: HTTP
                              url: https://example.test/health
                              method: GET
                          flows:
                            - name: Valid Breakpoint Flow
                              type: BREAKPOINT
                              steps:
                                - name: start
                                  type: START
                                  next_if_true: call
                                - name: call
                                  type: ENDPOINT
                                  endpoint: health
                            - name: Missing Plugin Flow
                              type: MISSING_PLUGIN
                              steps:
                                - name: start
                                  type: START
                        """.getBytes());

        MvcResult imported = mockMvc.perform(multipart("/api/v1/projects/import").file(spec))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Imported Multi Env"))
                .andReturn();
        long projectId = readLong(imported, "$.data.id");

        mockMvc.perform(get("/api/v1/projects/{projectId}/environments", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].name").value("Local"))
                .andExpect(jsonPath("$.data[1].name").value("Staging"));

        mockMvc.perform(get("/api/v1/projects/{projectId}/export", projectId))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("environments:")))
                .andExpect(content().string(containsString("name: Staging")))
                .andExpect(content().string(containsString("type: BREAKPOINT")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Missing Plugin Flow"))));
    }
}
