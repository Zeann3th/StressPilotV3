package dev.zeann3th.stresspilot.core.domain.entities;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "metric_scrape_events", indexes = {
    @Index(columnList = "run_id"),
    @Index(columnList = "collected_at")
})
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class MetricScrapeEventEntity extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "run_id", nullable = false)
    private RunEntity run;

    @Column(nullable = false)
    private String host;

    @Column(name = "collected_at", nullable = false)
    private LocalDateTime collectedAt;

    @Column(name = "source", length = 10)
    private String source;

    @Builder.Default
    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MetricValueEntity> values = new ArrayList<>();

    public String getRunId() {
        return run != null ? run.getId() : null;
    }
}
