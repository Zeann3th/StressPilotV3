package dev.zeann3th.stresspilot.ui.restful;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FunctionApiIntegrationTest extends AbstractApiIntegrationTest {

    @Test
    void createListDetailUpdateAndDeleteFunction() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/functions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"function-target-create","body":"return request;","description":"before"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.active").value(true))
                .andReturn();
        long functionId = readLong(created, "$.data.id");

        mockMvc.perform(get("/api/v1/functions").param("name", "function-target"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(functionId));

        mockMvc.perform(get("/api/v1/functions/{functionId}", functionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.body").value("return request;"));

        mockMvc.perform(put("/api/v1/functions/{functionId}", functionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"function-target-updated","body":"return response;","description":"after"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("function-target-updated"))
                .andExpect(jsonPath("$.data.body").value("return response;"));

        mockMvc.perform(delete("/api/v1/functions/{functionId}", functionId))
                .andExpect(status().isNoContent());
    }

    @Test
    void invalidFunctionRequestsReturnWrappedErrors() throws Exception {
        mockMvc.perform(post("/api/v1/functions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"","body":""}
                                """))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errorCode").value("ER9999"));

        mockMvc.perform(get("/api/v1/functions/{functionId}", 9_999_993L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errorCode").value("ER0025"));
    }
}
