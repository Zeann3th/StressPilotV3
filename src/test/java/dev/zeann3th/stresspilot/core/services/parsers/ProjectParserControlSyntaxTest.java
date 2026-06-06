package dev.zeann3th.stresspilot.core.services.parsers;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectParserControlSyntaxTest {

    private final ProjectParser parser = new ProjectParser();

    @Test
    void unmarshalSupportsStepRunSkipAndLoopSyntax() {
        var project = parser.unmarshal("""
                stresspilot:
                  project:
                    name: Control Syntax
                  flows:
                    - name: Flow
                      steps:
                        - name: start
                          type: START
                          next_if_true: loop-questions
                        - name: loop-questions
                          type: LOOP
                          source: questions
                          item: question
                          index: question_index
                          body: save-answer
                          next_if_true: submit
                        - name: save-answer
                          type: ENDPOINT
                          endpoint: save
                          run_if: "enabled == true"
                          skip_if: "question.id == null"
                          post_process:
                            clear:
                              - stale_answers
                            set:
                              answered: true
                            increment:
                              answer_count: 1
                            append:
                              answers:
                                question_id: "{{question.id}}"
                                chosen_choice_id: "{{question.choices.0.id}}"
                            serialize_json:
                              answers_json: answers
                """);

        var steps = project.getFlows().getFirst().getSteps();
        assertThat(steps.get(1).getPreProcess()).containsEntry("loop",
                java.util.Map.of(
                        "source", "questions",
                        "item", "question",
                        "index", "question_index",
                        "body", "save-answer"));
        assertThat(steps.get(2).getPreProcess())
                .containsEntry("run_if", "enabled == true")
                .containsEntry("skip_if", "question.id == null");
        assertThat(steps.get(2).getPostProcess())
                .containsEntry("clear", java.util.List.of("stale_answers"))
                .containsEntry("set", java.util.Map.of("answered", true))
                .containsEntry("increment", java.util.Map.of("answer_count", 1))
                .containsEntry("append", java.util.Map.of("answers", java.util.Map.of(
                        "question_id", "{{question.id}}",
                        "chosen_choice_id", "{{question.choices.0.id}}")))
                .containsEntry("serialize_json", java.util.Map.of("answers_json", "answers"));
    }
}
