package de.chriswohlbrecht.maintenance.persistence.model;

import de.chriswohlbrecht.maintenance.persistence.type.UserRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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

@Entity
@Table(name = "app_user")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(onlyExplicitlyIncluded = true)
public class AppUser {

    @ToString.Include

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Stored trimmed and lower-cased; unique. Null for household profiles. */
    @Column(unique = true)
    private String email;

    /** Name shown in the profile selection (household mode); stored trimmed, unique. */
    @Column(name = "display_name", length = 50, unique = true)
    private String displayName;

    /** Null until a password has been set (e.g. the initial admin before first startup). */
    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;

    @Builder.Default
    @Column(nullable = false)
    private Boolean active = true;

    @Builder.Default
    @Column(name = "email_verified", nullable = false)
    private Boolean emailVerified = false;

    @Builder.Default
    @Column(name = "failed_login_attempts", nullable = false)
    private Integer failedLoginAttempts = 0;

    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    /** ISO 639-1 code of the preferred language ("de" or "en"). */
    @Builder.Default
    @Column(nullable = false, length = 5)
    private String language = "de";

    /** ISO 4217 code of the currency used for new maintenance entries. */
    @Builder.Default
    @Column(nullable = false, length = 3)
    private String currency = "EUR";

    /** First day of the free trial (portal mode): the day the email address was verified. Null before that. */
    @Column(name = "trial_started_on")
    private LocalDate trialStartedOn;

    /**
     * Random reference under which the payment provider knows this account (portal mode); never the internal id.
     * Null until the first checkout.
     */
    @Column(name = "billing_customer_ref", length = 36, unique = true)
    private String billingCustomerRef;

    /** When the notice about the coming deletion of the expired account was sent; null without a pending notice. */
    @Column(name = "deletion_notice_sent_at")
    private LocalDateTime deletionNoticeSentAt;

    /**
     * The account is not deleted before this day: set when the person logs in after a deletion notice. Null
     * otherwise – login times are not recorded.
     */
    @Column(name = "keep_until")
    private LocalDate keepUntil;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
