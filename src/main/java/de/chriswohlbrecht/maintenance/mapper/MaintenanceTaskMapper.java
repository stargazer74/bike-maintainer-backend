package de.chriswohlbrecht.maintenance.mapper;

import de.chriswohlbrecht.maintenance.api.model.MaintenanceTaskRequest;
import de.chriswohlbrecht.maintenance.api.model.MaintenanceTaskResponse;
import de.chriswohlbrecht.maintenance.persistence.model.MaintenanceTask;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface MaintenanceTaskMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "vehicle", ignore = true)
    MaintenanceTask toEntity(MaintenanceTaskRequest request);

    @Mapping(target = "vehicleId", source = "vehicle.id")
    MaintenanceTaskResponse toResponse(MaintenanceTask task);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "vehicle", ignore = true)
    void updateEntityFromRequest(MaintenanceTaskRequest request, @MappingTarget MaintenanceTask task);
}
