package de.chriswohlbrecht.maintenance.controller;

import de.chriswohlbrecht.maintenance.api.handler.SubscriptionApi;
import de.chriswohlbrecht.maintenance.api.model.RedirectResponse;
import de.chriswohlbrecht.maintenance.component.ISubscriptionComponent;
import de.chriswohlbrecht.maintenance.configuration.OperatingModeCondition;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/** Checkout and customer portal of the payment provider; portal mode only (404 in household mode). */
@RestController
@RequiredArgsConstructor
@Conditional(OperatingModeCondition.Portal.class)
public class SubscriptionController implements SubscriptionApi {

    private final ISubscriptionComponent subscriptionComponent;

    @Override
    public ResponseEntity<RedirectResponse> createSubscriptionCheckout() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new RedirectResponse(URI.create(subscriptionComponent.startCheckout())));
    }

    @Override
    public ResponseEntity<RedirectResponse> createSubscriptionCustomerPortal() {
        return subscriptionComponent.openCustomerPortal()
                .map(url -> ResponseEntity.ok()
                        .cacheControl(CacheControl.noStore())
                        .body(new RedirectResponse(URI.create(url))))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
