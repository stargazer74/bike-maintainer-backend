package de.chriswohlbrecht.maintenance.mapper;

import de.chriswohlbrecht.maintenance.api.model.Currency;
import de.chriswohlbrecht.maintenance.api.model.MaintenanceLogRequest;
import de.chriswohlbrecht.maintenance.api.model.MaintenanceLogResponse;
import de.chriswohlbrecht.maintenance.api.model.MaintenanceMaterialRequest;
import de.chriswohlbrecht.maintenance.api.model.MaintenanceMaterialResponse;
import de.chriswohlbrecht.maintenance.persistence.model.MaintenanceLog;
import de.chriswohlbrecht.maintenance.persistence.model.MaintenanceLogMaterial;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

@Mapper(componentModel = "spring")
public interface MaintenanceLogMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "vehicle", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "currency", ignore = true)
    MaintenanceLog toEntity(MaintenanceLogRequest request);

    @Mapping(target = "vehicleId", source = "vehicle.id")
    @Mapping(target = "performedTaskIds", ignore = true)
    @Mapping(target = "materials", ignore = true)
    @Mapping(target = "totalCost", ignore = true)
    MaintenanceLogResponse toResponse(MaintenanceLog log);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "vehicle", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "currency", ignore = true)
    void updateEntityFromRequest(MaintenanceLogRequest request, @MappingTarget MaintenanceLog log);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "log", ignore = true)
    @Mapping(target = "task", ignore = true)
    @Mapping(target = "position", ignore = true)
    MaintenanceLogMaterial toMaterialEntity(MaintenanceMaterialRequest request);

    @Mapping(target = "taskId", source = "task.id")
    MaintenanceMaterialResponse toMaterialResponse(MaintenanceLogMaterial material);

    default Currency toCurrency(String currency) {
        return currency == null ? Currency.EUR : Currency.fromValue(currency);
    }

    default OffsetDateTime toOffsetDateTime(LocalDateTime value) {
        return value == null ? null : value.atZone(ZoneId.systemDefault()).toOffsetDateTime();
    }
}
