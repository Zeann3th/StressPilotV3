package dev.zeann3th.stresspilot.core.services.flows;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FlowProcessorControlTest {

    private final FlowProcessor processor = new FlowProcessor(new JsonMapper());

    @Test
    void shouldRunHonorsRunIfAndSkipIf() {
        Map<String, Object> variables = new HashMap<>();
        variables.put("needs_login", false);
        variables.put("enroll_students", false);

        assertThat(processor.shouldRun("{\"run_if\":\"needs_login == true\"}", variables, 0)).isFalse();
        assertThat(processor.shouldRun("{\"skip_if\":\"enroll_students == false\"}", variables, 0)).isFalse();

        variables.put("needs_login", true);
        variables.put("enroll_students", true);

        assertThat(processor.shouldRun("{\"run_if\":\"needs_login == true\"}", variables, 0)).isTrue();
        assertThat(processor.shouldRun("{\"skip_if\":\"enroll_students == false\"}", variables, 0)).isTrue();
    }

    @Test
    void processSetsAndIncrementsVariables() {
        Map<String, Object> variables = new HashMap<>();
        variables.put("question_index", 2);

        processor.process("""
                {
                  "set": { "has_token": true },
                  "increment": { "question_index": 1, "attempt": 1 }
                }
                """, variables, null, "test", 0);

        assertThat(variables)
                .containsEntry("has_token", true)
                .containsEntry("question_index", 3L)
                .containsEntry("attempt", 1L);
    }

    @Test
    void processAppendsInterpolatedObjectsAndSerializesJson() {
        Map<String, Object> variables = new HashMap<>();
        variables.put("question.id", 101);
        variables.put("question.choices.0.id", 1001);

        processor.process("""
                {
                  "append": {
                    "answers": {
                      "question_id": "{{question.id}}",
                      "chosen_choice_id": "{{question.choices.0.id}}"
                    }
                  },
                  "serialize_json": {
                    "answers_json": "answers"
                  }
                }
                """, variables, null, "test", 0);

        assertThat(variables.get("answers")).isEqualTo(java.util.List.of(
                Map.of("question_id", 101L, "chosen_choice_id", 1001L)));
        assertThat(variables).containsEntry("answers_json",
                "[{\"question_id\":101,\"chosen_choice_id\":1001}]");
    }
}
