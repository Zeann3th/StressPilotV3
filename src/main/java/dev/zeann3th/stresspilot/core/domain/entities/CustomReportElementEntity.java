package dev.zeann3th.stresspilot.core.domain.entities;

import dev.zeann3th.stresspilot.core.domain.enums.ReportElementType;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@Entity
@Table(name = "custom_report_elements")
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@ToString(onlyExplicitlyIncluded = true)
@EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
public class CustomReportElementEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @ToString.Include
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sheet_id", nullable = false)
    private CustomReportSheetEntity sheet;

    @Column(nullable = false)
    @ToString.Include
    private String name;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    @ToString.Include
    private ReportElementType type;

    @Column(columnDefinition = "TEXT")
    private String config;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;
}
