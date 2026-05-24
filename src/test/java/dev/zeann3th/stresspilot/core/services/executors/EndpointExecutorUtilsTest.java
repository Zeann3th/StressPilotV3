package dev.zeann3th.stresspilot.core.services.executors;

import dev.zeann3th.stresspilot.core.domain.commands.endpoint.ExecuteEndpointResponse;
import dev.zeann3th.stresspilot.core.domain.entities.EndpointEntity;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EndpointExecutorUtilsTest {

    @Test
    void leavesSuccessfulResponseAloneWhenConditionPasses() {
        EndpointEntity endpoint = EndpointEntity.builder()
                .successCondition("statusCode == 200")
                .build();
        ExecuteEndpointResponse response = ExecuteEndpointResponse.builder()
                .statusCode(200)
                .success(true)
                .build();

        EndpointExecutorUtils.evaluateSuccessCondition(endpoint, response);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getMessage()).isNull();
    }

    @Test
    void marksResponseFailedWhenConditionFailsOrCannotBeEvaluated() {
        EndpointEntity endpoint = EndpointEntity.builder()
                .successCondition("statusCode == 201")
                .build();
        ExecuteEndpointResponse response = ExecuteEndpointResponse.builder()
                .statusCode(200)
                .success(true)
                .build();

        EndpointExecutorUtils.evaluateSuccessCondition(endpoint, response);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getMessage()).contains("Condition failed");

        endpoint.setSuccessCondition("notAProperty == 1");
        response.setSuccess(true);
        EndpointExecutorUtils.evaluateSuccessCondition(endpoint, response);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getMessage()).contains("Eval Error");
    }
}
