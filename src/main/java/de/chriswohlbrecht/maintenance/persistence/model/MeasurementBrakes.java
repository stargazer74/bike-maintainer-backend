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

import java.math.BigDecimal;

/** Brake measurement of a maintenance log (at most one per log). */
@Entity
@Table(name = "measurement_brakes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MeasurementBrakes {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "log_id", nullable = false)
    private MaintenanceLog log;

    @Column(name = "pad_front", precision = 6, scale = 3)
    private BigDecimal padFront;

    @Column(name = "pad_rear", precision = 6, scale = 3)
    private BigDecimal padRear;

    @Column(name = "disc_front", precision = 6, scale = 3)
    private BigDecimal discFront;

    @Column(name = "disc_rear", precision = 6, scale = 3)
    private BigDecimal discRear;

    @Column(name = "disc_condition_front", length = 100)
    private String discConditionFront;

    @Column(name = "disc_condition_rear", length = 100)
    private String discConditionRear;

    @Column(name = "fluid_condition", length = 100)
    private String fluidCondition;
}
