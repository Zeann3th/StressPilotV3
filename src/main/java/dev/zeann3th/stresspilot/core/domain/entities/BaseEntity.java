package dev.zeann3th.stresspilot.core.domain.entities;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Getter
@Setter
@SuppressWarnings("unused")
@MappedSuperclass
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder(toBuilder = true)
public class BaseEntity {
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Column(name = "updated_at")
    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @PrePersist
    private void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
}
