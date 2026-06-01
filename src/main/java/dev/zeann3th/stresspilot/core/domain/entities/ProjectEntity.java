package dev.zeann3th.stresspilot.core.domain.entities;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "projects")
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class ProjectEntity extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @JsonIgnore
    @Column(name = "environment_id")
    private Long legacyEnvironmentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "active_environment_id")
    private EnvironmentEntity activeEnvironment;

    @Builder.Default
    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnoreProperties("project")
    @ToString.Exclude
    private List<EndpointEntity> endpoints = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnoreProperties("project")
    @ToString.Exclude
    private List<FlowEntity> flows = new ArrayList<>();

    public Long getEnvironmentId() {
        return getActiveEnvironmentId();
    }

    public Long getActiveEnvironmentId() {
        return activeEnvironment != null ? activeEnvironment.getId() : null;
    }
}
