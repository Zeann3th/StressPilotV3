package dev.zeann3th.stresspilot.ui.restful;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CustomReportApiIntegrationTest extends AbstractApiIntegrationTest {

    @Test
    void createSheet_then_listSheets_contains_new_sheet() throws Exception {
        mockMvc.perform(post("/api/v1/report-sheets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"My Sheet","displayOrder":0}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("My Sheet"));

        mockMvc.perform(get("/api/v1/report-sheets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.name == 'My Sheet')]").isNotEmpty());
    }

    @Test
    void createElement_then_sheetContainsElement() throws Exception {
        var result = mockMvc.perform(post("/api/v1/report-sheets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Sheet A","displayOrder":0}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        long sheetId = readLong(result, "$.data.id");

        mockMvc.perform(post("/api/v1/report-sheets/{id}/elements", sheetId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"Pass Rate",
                                  "type":"STAT",
                                  "config":"{\\"expression\\":\\"#report.successRate\\",\\"label\\":\\"Pass Rate\\",\\"unit\\":\\"%\\"}",
                                  "displayOrder":0
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.type").value("STAT"))
                .andExpect(jsonPath("$.data.name").value("Pass Rate"));
    }

    @Test
    void deleteSheet_returns_204() throws Exception {
        var result = mockMvc.perform(post("/api/v1/report-sheets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"ToDelete","displayOrder":0}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        long sheetId = readLong(result, "$.data.id");

        mockMvc.perform(delete("/api/v1/report-sheets/{id}", sheetId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/report-sheets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == " + sheetId + ")]").isEmpty());
    }

    @Test
    void updateSheet_name_is_persisted() throws Exception {
        var result = mockMvc.perform(post("/api/v1/report-sheets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Old Name","displayOrder":0}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        long sheetId = readLong(result, "$.data.id");

        mockMvc.perform(patch("/api/v1/report-sheets/{id}", sheetId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"New Name"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("New Name"));
    }
}
