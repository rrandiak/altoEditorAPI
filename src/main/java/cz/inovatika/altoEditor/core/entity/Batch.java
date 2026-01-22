package cz.inovatika.altoEditor.core.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

import cz.inovatika.altoEditor.core.enums.BatchPriority;
import cz.inovatika.altoEditor.core.enums.BatchState;
import cz.inovatika.altoEditor.core.enums.BatchSubstate;
import cz.inovatika.altoEditor.core.enums.BatchType;

/**
 * Batch entity with JPA annotations.
 */
@Entity
@Table(name = "batches")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Batch {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "batches_id_seq")
    @SequenceGenerator(name = "batches_id_seq", sequenceName = "batches_id_seq", allocationSize = 1)
    @Column(name = "id")
    private Integer id;

    @Column(name = "pid")
    private String pid;

    @Column(name = "create_date")
    private LocalDateTime createDate;

    @Column(name = "update_date")
    private LocalDateTime updateDate;

    @Column(name = "state")
    private BatchState state;

    @Column(name = "substate")
    private BatchSubstate substate;

    @Column(name = "priority")
    private BatchPriority priority;

    @Column(name = "type")
    private BatchType type;

    @Column(name = "instance")
    private String instance;

    @Column(name = "object_id")
    private Integer objectId;

    @Column(name = "estimated_item_count")
    private Integer estimatedItemCount;

    @Column(name = "log", columnDefinition = "TEXT")
    private String log;

    @PrePersist
    void created() {
        LocalDateTime now = LocalDateTime.now();
        this.createDate = now;
        this.updateDate = now;
    }

    @PreUpdate
    void updated() {
        this.updateDate = LocalDateTime.now();
    }
}
