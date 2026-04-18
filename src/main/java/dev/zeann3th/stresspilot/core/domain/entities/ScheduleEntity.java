package dev.zeann3th.stresspilot.core.domain.entities;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@Entity
@Table(name = "schedules")
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class ScheduleEntity extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "flow_id", nullable = false)
    private FlowEntity flow;

    @Column(name = "quartz_expr", nullable = false)
    private String quartzExpr;

    @Builder.Default
    @Column(name = "enabled", nullable = false)
    private Boolean enabled = true;

    @Column(name = "threads", nullable = false)
    private Integer threads;

    @Column(name = "duration", nullable = false)
    private Integer duration;

    @Column(name = "ramp_up", nullable = false)
    private Integer rampUp;
}
