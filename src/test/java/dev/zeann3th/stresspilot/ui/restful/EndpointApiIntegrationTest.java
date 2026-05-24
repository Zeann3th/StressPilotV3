package dev.zeann3th.stresspilot.ui.restful;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EndpointApiIntegrationTest extends AbstractApiIntegrationTest {

    @Test
    void createListDetailPatchAndDeleteEndpoint() throws Exception {
        ProjectFixture project = createProject("Endpoint API Target");
        long endpointId = createHttpEndpoint(project.id(), "Endpoint Create Target");

        mockMvc.perform(get("/api/v1/endpoints/{endpointId}", endpointId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.projectId").value(project.id()))
                .andExpect(jsonPath("$.data.httpMethod").value("POST"))
                .andExpect(jsonPath("$.data.httpHeaders").value("{\"Content-Type\":\"application/json\"}"));

        mockMvc.perform(get("/api/v1/endpoints")
                        .param("projectId", String.valueOf(project.id()))
                        .param("name", "endpoint create"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(endpointId));

        mockMvc.perform(patch("/api/v1/endpoints/{endpointId}", endpointId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"id":123,"projectId":123,"name":"Endpoint Patched","httpMethod":"PUT","body":"raw-body"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(endpointId))
                .andExpect(jsonPath("$.data.projectId").value(project.id()))
                .andExpect(jsonPath("$.data.name").value("Endpoint Patched"))
                .andExpect(jsonPath("$.data.body").value("raw-body"));

        mockMvc.perform(delete("/api/v1/endpoints/{endpointId}", endpointId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/endpoints/{endpointId}", endpointId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errorCode").value("ER0005"));
    }

    @Test
    void endpointValidationAndUploadErrorsAreWrapped() throws Exception {
        ProjectFixture project = createProject("Endpoint Validation Target");

        mockMvc.perform(post("/api/v1/endpoints")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"projectId":%d,"name":"bad","type":"HTTP","httpMethod":"GET"}
                                """.formatted(project.id())))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errorCode").value("ER9999"));

        mockMvc.perform(post("/api/v1/endpoints")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"projectId":%d,"name":"bad","type":"UNKNOWN"}
                                """.formatted(project.id())))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errorCode").value("ER9999"));

        MockMultipartFile emptyFile = new MockMultipartFile(
                "file", "openapi.json", MediaType.APPLICATION_JSON_VALUE, new byte[0]);
        mockMvc.perform(multipart("/api/v1/endpoints/upload")
                        .file(emptyFile)
                        .param("projectId", String.valueOf(project.id())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errorCode").value("ER0014"));
    }

    @Test
    void executeMissingEndpointAndAdhocMissingProjectReturnDomainErrors() throws Exception {
        mockMvc.perform(post("/api/v1/endpoints/{endpointId}/execute", 9_999_991L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"variables":{"token":"abc"}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errorCode").value("ER0005"));

        mockMvc.perform(post("/api/v1/endpoints/execute-adhoc")
                        .param("projectId", "9999992")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"HTTP","url":"https://example.test","httpMethod":"GET"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errorCode").value("ER0002"));
    }

    @Test
    void jsEndpointCanExecuteFunctionCreatedAfterApplicationStartup() throws Exception {
        ProjectFixture project = createProject("Endpoint JS Function Target");

        mockMvc.perform(post("/api/v1/functions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"decorate-after-startup",
                                  "body":"function decorateAfterStartup(value) { return env.prefix + '-' + value; }",
                                  "description":"UDF created by user after app startup"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.active").value(true));

        MvcResult created = mockMvc.perform(post("/api/v1/endpoints")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId":%d,
                                  "name":"JS calls new UDF",
                                  "type":"JS",
                                  "body":"decorateAfterStartup('payload')"
                                }
                                """.formatted(project.id())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type").value("JS"))
                .andReturn();
        long endpointId = readLong(created, "$.data.id");

        mockMvc.perform(post("/api/v1/endpoints/{endpointId}/execute", endpointId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"variables":{"prefix":"fresh"}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.success").value(true))
                .andExpect(jsonPath("$.data.rawResponse").value("fresh-payload"));
    }
}
