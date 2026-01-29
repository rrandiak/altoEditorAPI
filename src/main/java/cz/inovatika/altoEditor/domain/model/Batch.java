package cz.inovatika.altoEditor.domain.model;

import java.time.LocalDateTime;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

import cz.inovatika.altoEditor.domain.enums.BatchPriority;
import cz.inovatika.altoEditor.domain.enums.BatchState;
import cz.inovatika.altoEditor.domain.enums.BatchSubstate;
import cz.inovatika.altoEditor.domain.enums.BatchType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Batch entity with JPA annotations.
 * 
 * The batch can operate on specific PID, instance or digital object.
 * - Batch generating ALTO/OCR for pages or whole hierarchies should target PID.
 * - Batch for retrieving hierarchy structure from Kramerius should target PID and instance.
 * - Batch for processing specific digital object (like re-OCR, re-indexing, etc.)
 *   should target digital object ID.
 */
@Entity
@Table(name = "batches")
@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Batch {

    /**
     * Primary key identifier.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "batches_id_seq")
    @SequenceGenerator(name = "batches_id_seq", sequenceName = "batches_id_seq", allocationSize = 1)
    @Column(name = "id")
    private Integer id;

    /**
     * Type of this batch.
     */
    @Column(name = "type", nullable = false)
    private BatchType type;

    /**
     * PID of the target object for this batch.
     */
    @Column(name = "pid")
    private String pid;

    /**
     * Target Kramerius instance for this batch.
     */
    @Column(name = "instance")
    private String instance;

    /**
     * ID of the digital object associated with this batch.
     */
    @Column(name = "object_id")
    private Integer objectId;

    /**
     * Current state of this batch.
     */
    @Column(name = "state")
    @Builder.Default
    private BatchState state = BatchState.PLANNED;

    /**
     * Current substate of this batch.
     */
    @Column(name = "substate")
    private BatchSubstate substate;

    /**
     * Priority of this batch.
     */
    @Column(name = "priority")
    @Builder.Default
    private BatchPriority priority = BatchPriority.MEDIUM;

    /**
     * Date of creation of this batch.
     */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Date of last update of this batch.
     */
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Estimated number of items in this batch.
     */
    @Column(name = "estimated_item_count")
    private Integer estimatedItemCount;

    /**
     * Log information for this batch.
     */
    @Column(name = "log", columnDefinition = "TEXT")
    private String log;
}
