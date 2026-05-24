package dev.zeann3th.stresspilot.ui.restful;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EnvironmentApiIntegrationTest extends AbstractApiIntegrationTest {

    @Test
    void addUpdateListAndRemoveEnvironmentVariables() throws Exception {
        ProjectFixture project = createProject("Environment API Target");

        mockMvc.perform(get("/api/v1/environments/{environmentId}/variables", project.environmentId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(0));

        mockMvc.perform(patch("/api/v1/environments/{environmentId}/variables", project.environmentId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"added":[{"key":"baseUrl","value":"https://api.test"},{"key":"token","value":"abc"}]}
                                """))
                .andExpect(status().isNoContent());

        MvcResult list = mockMvc.perform(get("/api/v1/environments/{environmentId}/variables", project.environmentId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andReturn();
        long firstId = readLong(list, "$.data[0].id");

        mockMvc.perform(patch("/api/v1/environments/{environmentId}/variables", project.environmentId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"updated":[{"id":%d,"key":"apiBaseUrl","value":"https://patched.test","active":false}]}
                                """.formatted(firstId)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/environments/{environmentId}/variables", project.environmentId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].key").value("apiBaseUrl"))
                .andExpect(jsonPath("$.data[0].active").value(false));

        mockMvc.perform(patch("/api/v1/environments/{environmentId}/variables", project.environmentId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"removed":[%d]}
                                """.formatted(firstId)))
                .andExpect(status().isNoContent());
    }

    @Test
    void environmentVariableErrorsAreDomainWrapped() throws Exception {
        ProjectFixture project = createProject("Environment Error Target");

        mockMvc.perform(patch("/api/v1/environments/{environmentId}/variables", project.environmentId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"added":[{"key":"dup","value":"one"}]}
                                """))
                .andExpect(status().isNoContent());

        mockMvc.perform(patch("/api/v1/environments/{environmentId}/variables", project.environmentId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"added":[{"key":"dup","value":"two"}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errorCode").value("ER0018"));

        mockMvc.perform(patch("/api/v1/environments/{environmentId}/variables", project.environmentId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"updated":[{"id":99999991,"key":"missing","value":"x","active":true}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errorCode").value("ER0017"));

        mockMvc.perform(patch("/api/v1/environments/{environmentId}/variables", 9_999_992L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"added":[{"key":"x","value":"y"}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errorCode").value("ER0016"));
    }
}
