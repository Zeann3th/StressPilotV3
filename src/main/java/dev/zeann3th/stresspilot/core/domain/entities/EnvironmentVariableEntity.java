package dev.zeann3th.stresspilot.core.domain.entities;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@EqualsAndHashCode(callSuper = false)
@Data
@Entity
@Table(
    name = "environment_variables",
    uniqueConstraints = {
            @UniqueConstraint(columnNames = {"environment_id", "key"})
    })
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class EnvironmentVariableEntity extends BaseEntity {
    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "environment_id", nullable = false)
    @JsonIgnoreProperties("variables")
    @ToString.Exclude
    private EnvironmentEntity environment;

    @Column(name = "environment_id", insertable = false, updatable = false)
    private Long environmentId;

    @Column(name = "key", nullable = false)
    private String key;

    @Column(name = "value", columnDefinition = "TEXT")
    private String value;

    @Column(name = "is_active", nullable = false)
    private Boolean active;
}
