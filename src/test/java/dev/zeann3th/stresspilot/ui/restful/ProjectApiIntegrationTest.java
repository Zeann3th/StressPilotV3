package dev.zeann3th.stresspilot.ui.restful;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

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
                .andExpect(jsonPath("$.data.content[0].environmentId").value(project.environmentId()));

        mockMvc.perform(get("/api/v1/projects/{projectId}", project.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Project API Target"))
                .andExpect(jsonPath("$.data.environmentId").value(project.environmentId()));

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
}
