package dev.zeann3th.stresspilot.core.services.runs;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class RunServiceImplTest {

    @Test
    void extractsActiveThreadsFromStructuredVariablesSnapshot() throws Exception {
        assertThat(extract("""
                {"variables_snapshot":{"token":"abc","__stresspilot_active_threads":7}}
                """)).isEqualTo(7);
    }

    @Test
    void extractsActiveThreadsFromLegacyMarker() throws Exception {
        assertThat(extract("__stresspilot_active_threads=4")).isEqualTo(4);
    }

    private Integer extract(String request) throws Exception {
        RunServiceImpl service = new RunServiceImpl(null, null, null);
        Method method = RunServiceImpl.class.getDeclaredMethod("extractActiveThreads", String.class);
        method.setAccessible(true);
        return (Integer) method.invoke(service, request);
    }
}
