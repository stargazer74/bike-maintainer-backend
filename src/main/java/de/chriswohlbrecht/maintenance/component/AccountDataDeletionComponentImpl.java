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
import org.springframework.transaction.support.TransactionOperations;

import java.util.Optional;
import java.util.function.Predicate;

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
    private final TransactionOperations transactionOperations;

    /**
     * The provider first, outside of a transaction (its calls may take seconds): if it cannot be reached, the account
     * stays and nothing is billed for an account that no longer exists. Then everything in one transaction on the
     * locked, freshly read account.
     */
    @Override
    public boolean deleteWithAllData(AppUser user, Predicate<AppUser> stillDue) {
        subscriptionComponent.revokeSubscriptionsOf(user);
        return Boolean.TRUE.equals(transactionOperations.execute(status -> {
            Optional<AppUser> locked = appUserRepository.findLockedById(user.getId());
            if (locked.isEmpty() || !stillDue.test(locked.get())) {
                return false;
            }
            deleteLocked(locked.get());
            return true;
        }));
    }

    private void deleteLocked(AppUser user) {
        subscriptionComponent.removeSubscriptionsOf(user);

        // No ON DELETE CASCADE from the vehicle down: remove dependents first, children before parents.
        for (Vehicle vehicle : vehicleRepository.findAllByUser_Id(user.getId())) {
            maintenanceLogMaterialRepository.deleteAllByLog_Vehicle_Id(vehicle.getId());
            maintenanceLogTaskRepository.deleteAllByLog_Vehicle_Id(vehicle.getId());
            maintenanceLogRepository.deleteAllByVehicle_Id(vehicle.getId());
            maintenanceTaskRepository.deleteAllByVehicle_Id(vehicle.getId());
            vehicleRepository.delete(vehicle);
        }
        // Tokens and devices of the user go with it (ON DELETE CASCADE).
        appUserRepository.delete(user);
    }
}
