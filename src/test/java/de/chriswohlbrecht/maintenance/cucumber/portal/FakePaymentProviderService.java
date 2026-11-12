package de.chriswohlbrecht.maintenance.cucumber.portal;

import de.chriswohlbrecht.maintenance.component.model.ProviderOrder;
import de.chriswohlbrecht.maintenance.component.model.ProviderSubscription;
import de.chriswohlbrecht.maintenance.service.IPaymentProviderService;
import org.springframework.context.annotation.Primary;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** Replaces Polar in the portal Cucumber context: subscriptions are set by the steps, nothing leaves the test. */
@Primary
public class FakePaymentProviderService implements IPaymentProviderService {

    private final Set<String> knownCustomers = ConcurrentHashMap.newKeySet();
    private final Map<String, ProviderSubscription> subscriptions = new ConcurrentHashMap<>();
    private final List<String> revoked = new CopyOnWriteArrayList<>();

    @Override
    public String createCheckout(String customerRef, String email, String successUrl, String returnUrl) {
        knownCustomers.add(customerRef);
        return "https://polar.test/checkout/" + customerRef;
    }

    @Override
    public Optional<String> createCustomerPortal(String customerRef, String returnUrl) {
        return knownCustomers.contains(customerRef)
                ? Optional.of("https://polar.test/portal/" + customerRef) : Optional.empty();
    }

    @Override
    public Optional<ProviderSubscription> getSubscription(String subscriptionId) {
        return Optional.ofNullable(subscriptions.get(subscriptionId));
    }

    @Override
    public boolean revokeSubscription(String subscriptionId) {
        revoked.add(subscriptionId);
        return true;
    }

    @Override
    public Optional<ProviderOrder> findLastPeriodOrder(String subscriptionId) {
        return Optional.empty();
    }

    @Override
    public Optional<ProviderOrder> getOrder(String orderId) {
        return Optional.empty();
    }

    @Override
    public void refund(String orderId, long netAmount, String marker) {
        throw new UnsupportedOperationException("The scenarios do not refund");
    }

    public void put(ProviderSubscription subscription) {
        subscriptions.put(subscription.id(), subscription);
    }

    public List<String> revoked() {
        return List.copyOf(revoked);
    }

    public void clear() {
        knownCustomers.clear();
        subscriptions.clear();
        revoked.clear();
    }
}
