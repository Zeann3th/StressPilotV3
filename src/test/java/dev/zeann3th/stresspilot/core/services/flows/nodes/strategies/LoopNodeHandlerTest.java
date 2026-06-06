package dev.zeann3th.stresspilot.core.services.flows.nodes.strategies;

import dev.zeann3th.stresspilot.core.domain.entities.FlowStepEntity;
import dev.zeann3th.stresspilot.core.domain.enums.FlowStepType;
import dev.zeann3th.stresspilot.core.services.flows.FlowExecutionContext;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

class LoopNodeHandlerTest {

    private final LoopNodeHandler handler = new LoopNodeHandler(new JsonMapper());

    @Test
    void loopIteratesArrayAndFlattensCurrentItem() {
        FlowStepEntity step = FlowStepEntity.builder()
                .id("loop-questions")
                .type(FlowStepType.LOOP.name())
                .preProcessor("""
                        {
                          "loop": {
                            "source": "questions",
                            "item": "question",
                            "index": "question_index",
                            "body": "save-answer"
                          }
                        }
                        """)
                .nextIfTrue("submit")
                .build();

        Map<String, Object> variables = new ConcurrentHashMap<>();
        variables.put("questions", List.of(
                Map.of("id", 101, "choices", List.of(Map.of("id", 1001))),
                Map.of("id", 102, "choices", List.of(Map.of("id", 1002)))));
        FlowExecutionContext context = FlowExecutionContext.builder().variables(variables).build();

        var first = handler.handle(step, Map.of(), context);
        assertThat(first.nextId()).isEqualTo("save-answer");
        assertThat(variables)
                .containsEntry("question_index", 0)
                .containsEntry("question.id", 101)
                .containsEntry("question.choices.0.id", 1001);

        var second = handler.handle(step, Map.of(), context);
        assertThat(second.nextId()).isEqualTo("save-answer");
        assertThat(variables)
                .containsEntry("question_index", 1)
                .containsEntry("question.id", 102)
                .containsEntry("question.choices.0.id", 1002);

        var done = handler.handle(step, Map.of(), context);
        assertThat(done.nextId()).isEqualTo("submit");
        assertThat(variables).doesNotContainKeys("question", "question_index", "question.id");
    }

    @Test
    void loopCanIterateFixedCountWithoutSourceArray() {
        FlowStepEntity step = FlowStepEntity.builder()
                .id("loop-count")
                .type(FlowStepType.LOOP.name())
                .preProcessor("""
                        {
                          "loop": {
                            "count": 2,
                            "item": "attempt",
                            "index": "attempt_index",
                            "body": "run-attempt"
                          }
                        }
                        """)
                .nextIfTrue("finish")
                .build();
        FlowExecutionContext context = FlowExecutionContext.builder()
                .variables(new ConcurrentHashMap<>())
                .build();

        assertThat(handler.handle(step, Map.of(), context).nextId()).isEqualTo("run-attempt");
        assertThat(context.getVariables()).containsEntry("attempt", 0).containsEntry("attempt_index", 0);
        assertThat(handler.handle(step, Map.of(), context).nextId()).isEqualTo("run-attempt");
        assertThat(context.getVariables()).containsEntry("attempt", 1).containsEntry("attempt_index", 1);
        assertThat(handler.handle(step, Map.of(), context).nextId()).isEqualTo("finish");
    }
}
