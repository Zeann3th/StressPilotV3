package dev.zeann3th.stresspilot.ui.restful;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ConfigUtilityRunPluginApiIntegrationTest extends AbstractApiIntegrationTest {

    @Test
    void configCanReadDefaultsSetExistingAndCreateNewValues() throws Exception {
        mockMvc.perform(get("/api/v1/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.HTTP_CONNECT_TIMEOUT").exists())
                .andExpect(jsonPath("$.data.FLOW_ENDPOINT_STRICT_LINEAR").exists());

        mockMvc.perform(post("/api/v1/config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"key":"HTTP_CONNECT_TIMEOUT","value":"16"}
                                """))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/config/value").param("key", "HTTP_CONNECT_TIMEOUT"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"data\":\"16\"")));

        mockMvc.perform(post("/api/v1/config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"key":"CUSTOM_TEST_CONFIG","value":"enabled"}
                                """))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/config/value").param("key", "CUSTOM_TEST_CONFIG"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"data\":\"enabled\"")));
    }

    @Test
    void utilityRunAndPluginEndpointsReturnExpectedShapesAndErrors() throws Exception {
        mockMvc.perform(get("/api/v1/utilities/session"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isString());

        mockMvc.perform(get("/api/v1/utilities/capabilities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.endpointExecutors").isArray())
                .andExpect(jsonPath("$.data.flowExecutors").isArray())
                .andExpect(jsonPath("$.data.parsers").isArray());

        mockMvc.perform(get("/api/v1/runs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());

        mockMvc.perform(get("/api/v1/runs/{runId}", "missing-run"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errorCode").value("ER0010"));

        mockMvc.perform(get("/api/v1/runs/snapshot/compare/bad-format"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errorCode").value("ER0001"));

        mockMvc.perform(get("/api/v1/plugins/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }
}
