package dev.zeann3th.stresspilot.core.domain.entities;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@EqualsAndHashCode(callSuper = false)
@Data
@Entity
@Table(name = "flow_steps")
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class FlowStepEntity extends BaseEntity {
    @Id
    @Column(name = "id", columnDefinition = "VARCHAR(36)")
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "flow_id", nullable = false)
    @JsonIgnoreProperties("steps")
    @ToString.Exclude
    private FlowEntity flow;

    @Column(name = "type", columnDefinition = "VARCHAR(10)", nullable = false)
    private String type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "endpoint_id")
    @JsonIgnoreProperties("flowSteps")
    private EndpointEntity endpoint;

    @Column(name = "pre_processor", columnDefinition = "TEXT")
    private String preProcessor;

    @Column(name = "post_processor", columnDefinition = "TEXT")
    private String postProcessor;

    @Column(name = "next_if_true")
    private String nextIfTrue;

    @Column(name = "next_if_false")
    private String nextIfFalse;

    @Column(name = "condition", columnDefinition = "TEXT")
    private String condition;
}
