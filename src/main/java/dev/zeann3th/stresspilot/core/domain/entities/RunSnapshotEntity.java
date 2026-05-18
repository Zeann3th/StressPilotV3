package dev.zeann3th.stresspilot.core.domain.entities;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "runs_snapshot")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RunSnapshotEntity {

    @Id
    @Column(name = "id", length = 20, nullable = false, updatable = false)
    private String id;

    @Column(name = "flow_id", nullable = false)
    private Long flowId;

    @Column(name = "status", length = 10, nullable = false)
    private String status;

    @Column(name = "threads", nullable = false)
    private Integer threads;

    @Column(name = "duration", nullable = false)
    private Integer duration;

    @Column(name = "ramp_up_duration", nullable = false)
    private Integer rampUpDuration;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Column(name = "completed_at", nullable = false)
    private LocalDateTime completedAt;

    @Column(name = "metrics", columnDefinition = "TEXT", nullable = false)
    private String metrics;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
