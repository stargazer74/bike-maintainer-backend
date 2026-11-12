package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.configuration.OperatingModeCondition;
import de.chriswohlbrecht.maintenance.persistence.model.AppUser;
import de.chriswohlbrecht.maintenance.persistence.model.Vehicle;
import de.chriswohlbrecht.maintenance.persistence.repository.AppUserRepository;
import de.chriswohlbrecht.maintenance.persistence.repository.MaintenanceLogMaterialRepository;
import de.chriswohlbrecht.maintenance.persistence.repository.MaintenanceLogRepository;
import de.chriswohlbrecht.maintenance.persistence.repository.MaintenanceLogTaskRepository;
import de.chriswohlbrecht.maintenance.persistence.repository.MaintenanceTaskRepository;
import de.chriswohlbrecht.maintenance.persistence.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Used by the own deletion ({@link IAccountDeletionComponent}) and the deletion of expired accounts. */
@Component
@Conditional(OperatingModeCondition.Portal.class)
@RequiredArgsConstructor
public class AccountDataDeletionComponentImpl implements IAccountDataDeletionComponent {

    private final AppUserRepository appUserRepository;
    private final VehicleRepository vehicleRepository;
    private final MaintenanceTaskRepository maintenanceTaskRepository;
    private final MaintenanceLogRepository maintenanceLogRepository;
    private final MaintenanceLogTaskRepository maintenanceLogTaskRepository;
    private final MaintenanceLogMaterialRepository maintenanceLogMaterialRepository;
    private final ISubscriptionComponent subscriptionComponent;

    @Override
    @Transactional
    public void deleteWithAllData(AppUser user) {
        // Before anything is deleted: if the provider cannot be reached, the account stays and nothing is billed
        // for an account that no longer exists. The subscription rows go with the user (ON DELETE CASCADE).
        subscriptionComponent.endSubscriptionsOf(user);

        // No ON DELETE CASCADE from the vehicle down: remove dependents first, children before parents.
        for (Vehicle vehicle : vehicleRepository.findAllByUser_Id(user.getId())) {
            maintenanceLogMaterialRepository.deleteAllByLog_Vehicle_Id(vehicle.getId());
            maintenanceLogTaskRepository.deleteAllByLog_Vehicle_Id(vehicle.getId());
            maintenanceLogRepository.deleteAllByVehicle_Id(vehicle.getId());
            maintenanceTaskRepository.deleteAllByVehicle_Id(vehicle.getId());
            vehicleRepository.delete(vehicle);
        }
        // Tokens, devices and subscriptions of the user go with it (ON DELETE CASCADE).
        appUserRepository.delete(user);
    }
}
