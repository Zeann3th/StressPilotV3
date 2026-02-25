package dev.zeann3th.stresspilot.core.domain.entities;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@Entity
@Table(name = "configs")
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@ToString(onlyExplicitlyIncluded = true)
@EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
public class ConfigEntity extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @ToString.Include
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "config_key", nullable = false, unique = true)
    @ToString.Include
    private String key;

    @Column(name = "config_value", columnDefinition = "TEXT")
    @ToString.Include
    private String value;
}
