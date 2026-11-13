package de.chriswohlbrecht.maintenance.controller;

import de.chriswohlbrecht.maintenance.api.handler.WebhooksApi;
import de.chriswohlbrecht.maintenance.component.ISubscriptionComponent;
import de.chriswohlbrecht.maintenance.configuration.OperatingModeCondition;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Notifications of the payment provider; no session and no CSRF token, authenticated by their signature. */
@RestController
@RequiredArgsConstructor
@Conditional(OperatingModeCondition.Portal.class)
public class WebhookController implements WebhooksApi {

    private final ISubscriptionComponent subscriptionComponent;

    @Override
    public ResponseEntity<Void> receivePaddleWebhook(String paddleSignature, String body) {
        subscriptionComponent.handleWebhook(paddleSignature, body);
        return ResponseEntity.accepted().build();
    }
}
