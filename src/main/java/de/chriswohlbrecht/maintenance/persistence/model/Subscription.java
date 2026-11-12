package de.chriswohlbrecht.maintenance.persistence.model;

import de.chriswohlbrecht.maintenance.persistence.type.EarlyEndState;
import de.chriswohlbrecht.maintenance.persistence.type.SubscriptionProvider;
import de.chriswohlbrecht.maintenance.persistence.type.SubscriptionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** A subscription of an account with a payment provider; no payment data, only its state and paid period. */
@Entity
@Table(name = "subscription")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(onlyExplicitlyIncluded = true)
public class Subscription {

    @ToString.Include
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @ToString.Include
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SubscriptionProvider provider;

    /** Id of the subscription at the provider. */
    @Column(name = "external_id", nullable = false, length = 100)
    private String externalId;

    @ToString.Include
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SubscriptionStatus status;

    /** Last day of the paid period (inclusive); null while the provider has not reported one. */
    @Column(name = "paid_until")
    private LocalDate paidUntil;

    /** Day a cancellation after the first year was received; null otherwise (backend#147). */
    @Column(name = "early_end_received_on")
    private LocalDate earlyEndReceivedOn;

    /** Last day of access after a cancellation after the first year; null otherwise (backend#147). */
    @Column(name = "early_end_on")
    private LocalDate earlyEndOn;

    /** First day of the paid period at the time of that cancellation, for the pro-rata refund. */
    @Column(name = "early_end_period_start")
    private LocalDate earlyEndPeriodStart;

    /** Last day of the paid period at the time of that cancellation, for the pro-rata refund. */
    @Column(name = "early_end_period_end")
    private LocalDate earlyEndPeriodEnd;

    @Enumerated(EnumType.STRING)
    @Column(name = "early_end_state", length = 20)
    private EarlyEndState earlyEndState;

    /** Failed nightly attempts in a row; the early end is handed over to the administrators after a few. */
    @Builder.Default
    @Column(name = "early_end_attempts", nullable = false)
    private Integer earlyEndAttempts = 0;

    /** The provider's order whose rest is refunded; set before the refund is requested. */
    @Column(name = "early_end_order_id", length = 100)
    private String earlyEndOrderId;

    /** Amount of that order refunded before our refund, in cents; more than that afterwards means it went through. */
    @Column(name = "early_end_refunded_before")
    private Long earlyEndRefundedBefore;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
