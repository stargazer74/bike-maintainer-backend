package de.chriswohlbrecht.maintenance.mapper;

import de.chriswohlbrecht.maintenance.api.model.BrakeMeasurementRequest;
import de.chriswohlbrecht.maintenance.api.model.BrakeMeasurementResponse;
import de.chriswohlbrecht.maintenance.api.model.ChainMeasurementRequest;
import de.chriswohlbrecht.maintenance.api.model.ChainMeasurementResponse;
import de.chriswohlbrecht.maintenance.api.model.TireMeasurementRequest;
import de.chriswohlbrecht.maintenance.api.model.TireMeasurementResponse;
import de.chriswohlbrecht.maintenance.api.model.ValveMeasurementRequest;
import de.chriswohlbrecht.maintenance.api.model.ValveMeasurementResponse;
import de.chriswohlbrecht.maintenance.api.model.VehicleSpecifications;
import de.chriswohlbrecht.maintenance.persistence.model.MeasurementBrakes;
import de.chriswohlbrecht.maintenance.persistence.model.MeasurementChain;
import de.chriswohlbrecht.maintenance.persistence.model.MeasurementTires;
import de.chriswohlbrecht.maintenance.persistence.model.MeasurementValve;
import de.chriswohlbrecht.maintenance.persistence.model.VehicleSpecification;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** Target values and measured values; the status fields are computed by the component. */
@Mapper(componentModel = "spring")
public interface MeasurementMapper {

    VehicleSpecifications toApi(VehicleSpecification specification);

    @Mapping(target = "vehicleId", ignore = true)
    VehicleSpecification toEntity(VehicleSpecifications specifications);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "log", ignore = true)
    @Mapping(target = "valveIndex", source = "index")
    MeasurementValve toEntity(ValveMeasurementRequest request);

    @Mapping(target = "index", source = "valveIndex")
    @Mapping(target = "measuredStatus", ignore = true)
    ValveMeasurementResponse toResponse(MeasurementValve valve);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "log", ignore = true)
    MeasurementBrakes toEntity(BrakeMeasurementRequest request);

    @Mapping(target = "padFrontStatus", ignore = true)
    @Mapping(target = "padRearStatus", ignore = true)
    @Mapping(target = "discFrontStatus", ignore = true)
    @Mapping(target = "discRearStatus", ignore = true)
    BrakeMeasurementResponse toResponse(MeasurementBrakes brakes);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "log", ignore = true)
    MeasurementChain toEntity(ChainMeasurementRequest request);

    @Mapping(target = "slackStatus", ignore = true)
    @Mapping(target = "wearLengthStatus", ignore = true)
    ChainMeasurementResponse toResponse(MeasurementChain chain);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "log", ignore = true)
    MeasurementTires toEntity(TireMeasurementRequest request);

    @Mapping(target = "pressureFrontStatus", ignore = true)
    @Mapping(target = "pressureRearStatus", ignore = true)
    @Mapping(target = "treadFrontStatus", ignore = true)
    @Mapping(target = "treadRearStatus", ignore = true)
    TireMeasurementResponse toResponse(MeasurementTires tires);
}
