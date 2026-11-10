package de.chriswohlbrecht.maintenance.persistence.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDate;

/** Number of emails sent on one day; see {@code IMailQuotaComponent}. */
@Entity
@Table(name = "mail_quota")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString(onlyExplicitlyIncluded = true)
public class MailQuota {

    @ToString.Include

    @Id
    @Column(name = "quota_date")
    private LocalDate quotaDate;

    @Column(name = "sent_count", nullable = false)
    private Integer sentCount;
}
