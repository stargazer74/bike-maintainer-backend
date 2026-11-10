package de.chriswohlbrecht.maintenance.persistence.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;

/** Chain measurement of a maintenance log (at most one per log). */
@Entity
@Table(name = "measurement_chain")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(onlyExplicitlyIncluded = true)
public class MeasurementChain {

    @ToString.Include

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "log_id", nullable = false)
    private MaintenanceLog log;

    @Column(precision = 6, scale = 3)
    private BigDecimal slack;

    @Column(name = "wear_length", precision = 6, scale = 3)
    private BigDecimal wearLength;

    @Column(name = "wear_links")
    private Integer wearLinks;

    @Column(name = "sprocket_condition", length = 100)
    private String sprocketCondition;

    @Column(name = "rear_sprocket_condition", length = 100)
    private String rearSprocketCondition;

    private Boolean cleaned;

    private Boolean lubricated;
}
