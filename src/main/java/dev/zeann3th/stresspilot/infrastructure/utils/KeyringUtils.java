package dev.zeann3th.stresspilot.infrastructure.utils;

import com.github.javakeyring.Keyring;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Properties;

@Slf4j(topic = "[KeyringUtils]")
@UtilityClass
public class KeyringUtils {
    private static final String KEYRING_SERVICE = "stresspilot";
    private static final String KEYRING_ACCOUNT = "pilot";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    public static String getOrCreateKey() {
        try (Keyring keyring = Keyring.create()) {
            try {
                String existing = keyring.getPassword(KEYRING_SERVICE, KEYRING_ACCOUNT);
                if (existing != null && !existing.isBlank()) {
                    log.info("DB key loaded from OS keystore");
                    return existing;
                }
            } catch (Exception _) {
                // not found yet — fall through to create
            }

            String newKey = generateKey();
            keyring.setPassword(KEYRING_SERVICE, KEYRING_ACCOUNT, newKey);
            log.info("New DB key generated and stored in OS keystore");
            return newKey;

        } catch (Exception e) {
            log.warn("OS keystore unavailable, using fallback key: {}", e.getMessage());
            return fallbackKey();
        }
    }

    public static String rotateKey(DataSource dataSource) throws Exception {
        String newKey = generateKey();

        try (var conn = dataSource.getConnection();
             var stmt = conn.createStatement()) {
            stmt.execute("PRAGMA journal_mode = DELETE;");

            stmt.execute("PRAGMA rekey = '" + newKey + "';");

            stmt.execute("PRAGMA journal_mode = WAL;");
        }

        try (Keyring keyring = Keyring.create()) {
            keyring.setPassword(KEYRING_SERVICE, KEYRING_ACCOUNT, newKey);
        }

        if (dataSource instanceof DriverManagerDataSource ds) {
            Properties props = new Properties();
            props.setProperty("key", newKey);
            ds.setConnectionProperties(props);
        }

        log.info("DB key rotated successfully");
        return newKey;
    }

    private static String generateKey() {
        byte[] raw = new byte[64];
        SECURE_RANDOM.nextBytes(raw);
        return HexFormat.of().formatHex(raw);
    }

    private static String fallbackKey() {
        String seed = System.getProperty("user.name")
                + System.getProperty("user.home")
                + System.getProperty("os.name");
        return HexFormat.of().formatHex(seed.getBytes());
    }
}
