package dev.zeann3th.stresspilot.core.domain.constants;

import lombok.experimental.UtilityClass;

import java.util.List;

@UtilityClass
public class Constants {
    public static final String APP_NAME = "stresspilot";
    public static final String APP_DIR = ".pilot";
    public static final String DB_FILE_NAME = "data.sqlite";
    public static final String REASON = "reason";
    public static final String ID = "id";
    public static final String TYPE = "type";
    public static final String KEY = "key";
    public static final String ERROR = "error";
    public static final String PILOT_HOME = "PILOT_HOME";
    public static final String USER_HOME = "user.home";

    public static final List<String> PUBLIC_PATHS = List.of(
            "/api/v1/utilities/session",
            "/api/v1/utilities/healthz",
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/ws/**"
    );
}
