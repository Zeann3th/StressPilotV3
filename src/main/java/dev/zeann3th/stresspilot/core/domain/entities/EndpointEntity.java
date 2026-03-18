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
@Table(name = "endpoints")
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
public class EndpointEntity extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    @ToString.Include
    @EqualsAndHashCode.Include
    private Long id;

    // Metadata
    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "type", columnDefinition = "VARCHAR(20)", nullable = false)
    private String type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private ProjectEntity project;

    @Column(name = "url")
    private String url;

    @Column(name = "body", columnDefinition = "TEXT")
    private String body;

    @Column(name = "success_condition", columnDefinition = "TEXT")
    private String successCondition;

    // Http
    @Column(name = "http_method", columnDefinition = "VARCHAR(10)")
    private String httpMethod;

    @Column(name = "http_headers", columnDefinition = "TEXT")
    private String httpHeaders;

    @Column(name = "http_parameters", columnDefinition = "TEXT")
    private String httpParameters;

    // gRPC
    @Column(name = "grpc_service_name")
    private String grpcServiceName;

    @Column(name = "grpc_method_name")
    private String grpcMethodName;

    @Column(name = "grpc_stub_path", columnDefinition = "TEXT")
    private String grpcStubPath;

    public Long getProjectId() {
        return project != null ? project.getId() : null;
    }

    @Builder.Default
    @OneToMany(mappedBy = "endpoint", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnoreProperties("endpoint")
    @ToString.Exclude
    private List<RequestLogEntity> requestLogs =  new ArrayList<>();

    @Override
    public String toString() {
        return switch (type.toUpperCase()) {
            case "HTTP" -> String.format("[HTTP] %s %s", httpMethod, url);
            case "GRPC" -> String.format("[gRPC] %s.%s (stub: %s)", grpcServiceName, grpcMethodName, grpcStubPath);
            case "JDBC" -> String.format("[JDBC] %s (query: %s) ", url, body);
            default -> super.toString();
        };
    }
}
