package de.chriswohlbrecht.maintenance.persistence.model;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "maintenance_log_task")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MaintenanceLogTask {

    @EmbeddedId
    private MaintenanceLogTaskId id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @MapsId("logId")
    @JoinColumn(name = "log_id", nullable = false)
    private MaintenanceLog log;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @MapsId("taskId")
    @JoinColumn(name = "task_id", nullable = false)
    private MaintenanceTask task;
}
