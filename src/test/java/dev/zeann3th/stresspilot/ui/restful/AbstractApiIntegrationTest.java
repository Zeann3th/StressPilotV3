package dev.zeann3th.stresspilot.ui.restful;

import com.jayway.jsonpath.JsonPath;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.zeann3th.stresspilot.StresspilotApplication;

@SpringBootTest(classes = StresspilotApplication.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
abstract class AbstractApiIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    protected ProjectFixture createProject(String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","description":"%s description"}
                                """.formatted(name, name)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.errorCode").value("ER0000"))
                .andReturn();
        return new ProjectFixture(
                readLong(result, "$.data.id"),
                readLong(result, "$.data.environmentId"));
    }

    protected long createHttpEndpoint(long projectId, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/endpoints")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId":%d,
                                  "name":"%s",
                                  "description":"HTTP endpoint",
                                  "type":"HTTP",
                                  "url":"https://example.test/%s",
                                  "httpMethod":"POST",
                                  "httpHeaders":{"Content-Type":"application/json"},
                                  "httpParameters":{"tenant":"stresspilot"},
                                  "body":{"sku":"A-1"},
                                  "successCondition":"statusCode == 201"
                                }
                                """.formatted(projectId, name, name.toLowerCase().replace(" ", "-"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value(name))
                .andReturn();
        return readLong(result, "$.data.id");
    }

    protected long createFlow(long projectId, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/flows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"projectId":%d,"name":"%s","description":"flow","type":"DEFAULT"}
                                """.formatted(projectId, name)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.type").value("DEFAULT"))
                .andReturn();
        return readLong(result, "$.data.id");
    }

    protected void configureLinearFlow(long flowId, long endpointId) throws Exception {
        mockMvc.perform(post("/api/v1/flows/{flowId}/configuration", flowId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [
                                  {"id":"start","type":"START","nextIfTrue":"call"},
                                  {"id":"call","type":"ENDPOINT","endpointId":%d,
                                   "preProcessor":{"token":"{{ authToken }}"},
                                   "postProcessor":{"capture":"$.id"}}
                                ]
                                """.formatted(endpointId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    protected long readLong(MvcResult result, String path) throws Exception {
        Object value = JsonPath.read(result.getResponse().getContentAsString(), path);
        assertThat(value).isInstanceOf(Number.class);
        return ((Number) value).longValue();
    }

    protected String readString(MvcResult result, String path) throws Exception {
        Object value = JsonPath.read(result.getResponse().getContentAsString(), path);
        assertThat(value).isInstanceOf(String.class);
        return (String) value;
    }

    protected record ProjectFixture(long id, long environmentId) {
    }
}
