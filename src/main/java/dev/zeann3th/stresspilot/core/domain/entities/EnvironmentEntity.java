package dev.zeann3th.stresspilot.core.domain.entities;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "environments")
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class EnvironmentEntity extends BaseEntity {
    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    @JsonIgnoreProperties({ "activeEnvironment", "endpoints", "flows" })
    @ToString.Exclude
    private ProjectEntity project;

    @Builder.Default
    @OneToMany(mappedBy = "environment", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnoreProperties("environment")
    @ToString.Exclude
    private List<EnvironmentVariableEntity> variables = new ArrayList<>();
}
