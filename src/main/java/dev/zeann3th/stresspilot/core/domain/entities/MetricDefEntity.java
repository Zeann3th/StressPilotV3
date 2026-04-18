package dev.zeann3th.stresspilot.core.domain.entities;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@Entity
@Table(name = "metric_defs",
    uniqueConstraints = @UniqueConstraint(columnNames = "name"))
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class MetricDefEntity extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    private String unit;

    private String description;
}
