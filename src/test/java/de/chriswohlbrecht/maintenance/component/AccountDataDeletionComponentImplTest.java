package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.exception.PaymentProviderUnavailableException;
import de.chriswohlbrecht.maintenance.persistence.model.AppUser;
import de.chriswohlbrecht.maintenance.persistence.model.Vehicle;
import de.chriswohlbrecht.maintenance.persistence.repository.AppUserRepository;
import de.chriswohlbrecht.maintenance.persistence.repository.MaintenanceLogMaterialRepository;
import de.chriswohlbrecht.maintenance.persistence.repository.MaintenanceLogRepository;
import de.chriswohlbrecht.maintenance.persistence.repository.MaintenanceLogTaskRepository;
import de.chriswohlbrecht.maintenance.persistence.repository.MaintenanceTaskRepository;
import de.chriswohlbrecht.maintenance.persistence.repository.VehicleRepository;
import de.chriswohlbrecht.maintenance.persistence.type.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionOperations;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountDataDeletionComponentImplTest {

    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private VehicleRepository vehicleRepository;
    @Mock
    private MaintenanceTaskRepository maintenanceTaskRepository;
    @Mock
    private MaintenanceLogRepository maintenanceLogRepository;
    @Mock
    private MaintenanceLogTaskRepository maintenanceLogTaskRepository;
    @Mock
    private MaintenanceLogMaterialRepository maintenanceLogMaterialRepository;
    @Mock
    private ISubscriptionComponent subscriptionComponent;

    private AccountDataDeletionComponentImpl component;
    private final AppUser user = AppUser.builder().id(7L).email("rider@example.org").role(UserRole.USER).build();

    @BeforeEach
    void setUp() {
        component = new AccountDataDeletionComponentImpl(appUserRepository, vehicleRepository,
                maintenanceTaskRepository, maintenanceLogRepository, maintenanceLogTaskRepository,
                maintenanceLogMaterialRepository, subscriptionComponent, TransactionOperations.withoutTransaction());
        lenient().when(appUserRepository.findLockedById(7L)).thenReturn(Optional.of(user));
    }

    @Test
    void endsSubscriptionsThenDeletesVehiclesWithDependentsThenUser() {
        Vehicle first = Vehicle.builder().id(11L).build();
        Vehicle second = Vehicle.builder().id(12L).build();
        when(vehicleRepository.findAllByUser_Id(7L)).thenReturn(List.of(first, second));

        assertThat(component.deleteWithAllData(user)).isTrue();

        InOrder order = inOrder(subscriptionComponent, maintenanceLogMaterialRepository, maintenanceLogTaskRepository,
                maintenanceLogRepository, maintenanceTaskRepository, vehicleRepository, appUserRepository);
        order.verify(subscriptionComponent).revokeSubscriptionsOf(user);
        order.verify(appUserRepository).findLockedById(7L);
        order.verify(subscriptionComponent).removeSubscriptionsOf(user);
        for (Vehicle vehicle : List.of(first, second)) {
            order.verify(maintenanceLogMaterialRepository).deleteAllByLog_Vehicle_Id(vehicle.getId());
            order.verify(maintenanceLogTaskRepository).deleteAllByLog_Vehicle_Id(vehicle.getId());
            order.verify(maintenanceLogRepository).deleteAllByVehicle_Id(vehicle.getId());
            order.verify(maintenanceTaskRepository).deleteAllByVehicle_Id(vehicle.getId());
            order.verify(vehicleRepository).delete(vehicle);
        }
        order.verify(appUserRepository).delete(user);
    }

    @Test
    void userWithoutVehiclesIsDeleted() {
        when(vehicleRepository.findAllByUser_Id(7L)).thenReturn(List.of());

        component.deleteWithAllData(user);

        verify(appUserRepository).delete(user);
        verifyNoInteractions(maintenanceTaskRepository, maintenanceLogRepository);
    }

    /** If the provider cannot end the subscription, the account stays, so nothing is billed for a deleted one. */
    @Test
    void unreachablePaymentProviderDeletesNothing() {
        doThrow(new PaymentProviderUnavailableException("down")).when(subscriptionComponent)
                .revokeSubscriptionsOf(user);

        assertThatThrownBy(() -> component.deleteWithAllData(user))
                .isInstanceOf(PaymentProviderUnavailableException.class);

        verify(appUserRepository, never()).delete(any());
        verifyNoInteractions(vehicleRepository);
    }

    /** E.g. a login since the retention run decided: the locked, fresh account is checked once more. */
    @Test
    void accountThatIsNoLongerDueIsKept() {
        assertThat(component.deleteWithAllData(user, current -> false)).isFalse();

        verify(subscriptionComponent, never()).removeSubscriptionsOf(any());
        verify(appUserRepository, never()).delete(any());
        verifyNoInteractions(vehicleRepository);
    }

    @Test
    void accountDeletedMeanwhileIsNotDeletedAgain() {
        when(appUserRepository.findLockedById(7L)).thenReturn(Optional.empty());

        assertThat(component.deleteWithAllData(user)).isFalse();

        verify(appUserRepository, never()).delete(any());
    }
}
