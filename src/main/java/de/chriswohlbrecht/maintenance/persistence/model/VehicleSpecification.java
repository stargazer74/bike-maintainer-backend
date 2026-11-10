package de.chriswohlbrecht.maintenance.persistence.model;

import de.chriswohlbrecht.maintenance.persistence.type.ValveAdjustment;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;

/** Target values of a vehicle from the workshop manual (Epic #113); all optional. Lengths in mm, pressures in bar. */
@Entity
@Table(name = "vehicle_specification")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(onlyExplicitlyIncluded = true)
public class VehicleSpecification {

    @ToString.Include

    @Id
    @Column(name = "vehicle_id")
    private Long vehicleId;

    private Integer cylinders;

    @Column(name = "valves_per_cylinder")
    private Integer valvesPerCylinder;

    @Enumerated(EnumType.STRING)
    @Column(name = "valve_adjustment", length = 10)
    private ValveAdjustment valveAdjustment;

    @Column(name = "intake_clearance_min", precision = 6, scale = 3)
    private BigDecimal intakeClearanceMin;

    @Column(name = "intake_clearance_max", precision = 6, scale = 3)
    private BigDecimal intakeClearanceMax;

    @Column(name = "exhaust_clearance_min", precision = 6, scale = 3)
    private BigDecimal exhaustClearanceMin;

    @Column(name = "exhaust_clearance_max", precision = 6, scale = 3)
    private BigDecimal exhaustClearanceMax;

    @Column(name = "shim_step", precision = 6, scale = 3)
    private BigDecimal shimStep;

    @Column(name = "shim_min", precision = 6, scale = 3)
    private BigDecimal shimMin;

    @Column(name = "shim_max", precision = 6, scale = 3)
    private BigDecimal shimMax;

    @Column(name = "brake_pad_min_front", precision = 6, scale = 3)
    private BigDecimal brakePadMinFront;

    @Column(name = "brake_pad_min_rear", precision = 6, scale = 3)
    private BigDecimal brakePadMinRear;

    @Column(name = "brake_disc_min_front", precision = 6, scale = 3)
    private BigDecimal brakeDiscMinFront;

    @Column(name = "brake_disc_min_rear", precision = 6, scale = 3)
    private BigDecimal brakeDiscMinRear;

    @Column(name = "chain_slack_min", precision = 6, scale = 3)
    private BigDecimal chainSlackMin;

    @Column(name = "chain_slack_max", precision = 6, scale = 3)
    private BigDecimal chainSlackMax;

    @Column(name = "chain_wear_links")
    private Integer chainWearLinks;

    @Column(name = "chain_wear_max_length", precision = 6, scale = 3)
    private BigDecimal chainWearMaxLength;

    @Column(name = "tire_pressure_front", precision = 4, scale = 2)
    private BigDecimal tirePressureFront;

    @Column(name = "tire_pressure_rear", precision = 4, scale = 2)
    private BigDecimal tirePressureRear;

    @Column(name = "tread_depth_min", precision = 6, scale = 3)
    private BigDecimal treadDepthMin;
}
