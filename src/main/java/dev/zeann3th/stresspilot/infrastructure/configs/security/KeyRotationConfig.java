package dev.zeann3th.stresspilot.infrastructure.configs.security;

import com.github.javakeyring.Keyring;
import dev.zeann3th.stresspilot.infrastructure.utils.KeyringUtils;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j(topic = "[KeyRotation]")
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "application.mode", havingValue = "desktop")
public class KeyRotationConfig {
    private static final String KEYRING_SERVICE = "stresspilot";
    private static final String KEYRING_ROTATION_ACCOUNT = "pilot-rotated-at";
    private static final int ROTATION_DAYS = 30;

    private final DataSource dataSource;

    @PostConstruct
    public void rotateIfNeeded() {
        try {
            LocalDateTime lastRotated = null;
            try (var keyring = com.github.javakeyring.Keyring.create()) {
                String raw = keyring.getPassword(KEYRING_SERVICE, KEYRING_ROTATION_ACCOUNT);
                if (raw != null && !raw.isBlank()) {
                    lastRotated = LocalDateTime.parse(raw, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                }
            } catch (Exception _) {
                // never rotated before
            }

            if (lastRotated != null) {
                log.info("Last key rotation: {}", lastRotated);
            }

            boolean shouldRotate = lastRotated == null
                    || lastRotated.isBefore(LocalDateTime.now().minusDays(ROTATION_DAYS));

            if (!shouldRotate) {
                log.info("Key rotation not needed, next rotation at {}",
                        lastRotated.plusDays(ROTATION_DAYS));
                return;
            }

            log.info("Rotating DB key...");
            KeyringUtils.rotateKey(dataSource);

            try (var keyring = Keyring.create()) {
                keyring.setPassword(KEYRING_SERVICE, KEYRING_ROTATION_ACCOUNT,
                        LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            } catch (Exception e) {
                log.warn("Could not save rotation timestamp: {}", e.getMessage());
            }

            log.info("Key rotation completed successfully");

        } catch (Exception e) {
            log.warn("Key rotation failed, keeping existing key: {}", e.getMessage());
        }
    }
}
