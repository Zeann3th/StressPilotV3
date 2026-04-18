package dev.zeann3th.stresspilot.core.domain.entities;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@Entity
@Table(name = "metric_values", indexes = {
    @Index(columnList = "event_id"),
    @Index(columnList = "def_id")
})
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class MetricValueEntity extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private MetricScrapeEventEntity event;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "def_id", nullable = false)
    private MetricDefEntity def;

    @Column(nullable = false)
    private Double value;

    @Column(columnDefinition = "TEXT")
    private String labels;
}
