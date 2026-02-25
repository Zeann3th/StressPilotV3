package dev.zeann3th.stresspilot.core.domain.entities;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@EqualsAndHashCode(callSuper = false)
@Data
@Entity
@Table(name = "runs")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RunEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "flow_id", nullable = false)
    private FlowEntity flow;

    @Column(name = "status", columnDefinition = "VARCHAR(10)", nullable = false)
    private String status;

    @Column(name = "threads", nullable = false)
    private Integer threads;

    @Column(name = "duration", nullable = false)
    private Integer duration;

    @Column(name = "ramp_up_duration", nullable = false)
    private Integer rampUpDuration;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Column(name = "started_at", nullable = false, updatable = false)
    private LocalDateTime startedAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Builder.Default
    @OneToMany(mappedBy = "run", cascade = CascadeType.ALL)
    @JsonIgnoreProperties("run")
    @ToString.Exclude
    private List<RequestLogEntity> requestLogs = new ArrayList<>();
}
