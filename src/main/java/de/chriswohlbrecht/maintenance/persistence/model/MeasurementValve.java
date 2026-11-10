package de.chriswohlbrecht.maintenance.persistence.model;

import de.chriswohlbrecht.maintenance.persistence.type.ValveKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;

/** Valve clearance of one valve, measured during a maintenance log. */
@Entity
@Table(name = "measurement_valve")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(onlyExplicitlyIncluded = true)
public class MeasurementValve {

    @ToString.Include

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "log_id", nullable = false)
    private MaintenanceLog log;

    @Column(nullable = false)
    private int cylinder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ValveKind valve;

    @Column(name = "valve_index", nullable = false)
    private int valveIndex;

    @Column(precision = 6, scale = 3)
    private BigDecimal measured;

    @Column(name = "shim_old", precision = 6, scale = 3)
    private BigDecimal shimOld;

    @Column(name = "shim_new", precision = 6, scale = 3)
    private BigDecimal shimNew;
}
