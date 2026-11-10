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

/** Tyre measurement of a maintenance log (at most one per log). */
@Entity
@Table(name = "measurement_tires")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(onlyExplicitlyIncluded = true)
public class MeasurementTires {

    @ToString.Include

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "log_id", nullable = false)
    private MaintenanceLog log;

    @Column(name = "pressure_front", precision = 4, scale = 2)
    private BigDecimal pressureFront;

    @Column(name = "pressure_rear", precision = 4, scale = 2)
    private BigDecimal pressureRear;

    @Column(name = "tread_front", precision = 6, scale = 3)
    private BigDecimal treadFront;

    @Column(name = "tread_rear", precision = 6, scale = 3)
    private BigDecimal treadRear;

    @Column(name = "dot_front", length = 4)
    private String dotFront;

    @Column(name = "dot_rear", length = 4)
    private String dotRear;

    @Column(name = "condition_front", length = 100)
    private String conditionFront;

    @Column(name = "condition_rear", length = 100)
    private String conditionRear;
}
