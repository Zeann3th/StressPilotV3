package dev.zeann3th.stresspilot.core.domain.entities;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@EqualsAndHashCode(callSuper = false)
@Data
@Entity
@Table(name = "request_logs")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RequestLogEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "run_id", nullable = false)
    @JsonIgnoreProperties("requestLogs")
    @ToString.Exclude
    private RunEntity run;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "endpoint_id", nullable = false)
    @JsonIgnoreProperties("requestLogs")
    private EndpointEntity endpoint;

    @Column(name = "status_code", nullable = false)
    private Integer statusCode;

    @Column(name = "is_success", nullable = false)
    private Boolean success;

    @Column(name = "response_time", nullable = false)
    private Long responseTime;

    @Column(name = "request", columnDefinition = "TEXT")
    private String request;

    @Column(name = "response", columnDefinition = "TEXT")
    private String response;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
