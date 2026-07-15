package cz.inovatika.altoEditor.domain.model;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import cz.inovatika.altoEditor.domain.enums.BatchPriority;
import cz.inovatika.altoEditor.domain.enums.BatchState;
import cz.inovatika.altoEditor.domain.enums.BatchSubstate;
import cz.inovatika.altoEditor.domain.enums.BatchType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Batch entity with JPA annotations.
 * 
 * The batch can operate on specific PID, digital object or instance.
 * - Batch generating ALTO/OCR for pages or whole hierarchies should target PID.
 * - Batch for processing specific digital object (like re-OCR, re-indexing, etc.)
 *   should target digital object ID.
 * - Batch for retrieving hierarchy structure from Kramerius should target PID and instance.
 */
@Entity
@Table(name = "batches", indexes = {
        @Index(columnList = "state"),
        @Index(columnList = "created_by"),
        @Index(columnList = "parent_batch_id")
})
@EntityListeners(AuditingEntityListener.class)
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
     * Priority of this batch.
     */
    @Column(name = "priority")
    @Builder.Default
    private BatchPriority priority = BatchPriority.MEDIUM;

    /**
     * Target Kramerius instance for this batch.
     */
    @Column(name = "instance")
    private String instance;

    /**
     * PID of the target object for this batch.
     */
    @Column(name = "pid")
    private String pid;

    /**
     * ID of the ALTO version associated with this batch.
     */
    @Column(name = "alto_version_id")
    private Integer altoVersionId;

    /**
     * Target engine for this batch.
     */
    @Column(name = "engine")
    private String engine;

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
     * User who created this batch. LAZY to avoid N+1 when listing batches;
     * use JOIN FETCH / EntityGraph when createdBy is needed.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", referencedColumnName = "id", nullable = false)
    private User createdBy;

    /**
     * Estimated number of items in this batch.
     */
    @Column(name = "estimated_item_count")
    private Integer estimatedItemCount;

    /**
     * Number of processed items in this batch.
     */
    @Column(name = "processed_item_count")
    private Integer processedItemCount;

    /**
     * Log information for this batch.
     */
    @Column(name = "log", columnDefinition = "TEXT")
    private String log;

    /**
     * Optional JSON payload for process-specific input (e.g. {@link cz.inovatika.altoEditor.domain.model.dto.HierarchyGenerateInput} for GENERATE_FOR_HIERARCHY).
     */
    @Column(name = "data", columnDefinition = "TEXT")
    private String data;

    /**
     * For a pipeline child stage: the id of its parent {@link BatchType#PIPELINE} batch.
     * Null for the parent and for all standalone (non-pipeline) batches.
     */
    @Column(name = "parent_batch_id")
    private Integer parentBatchId;

    /**
     * For a pipeline child stage: 0-based position within the pipeline (compacted over the
     * selected stages). The scheduler will not claim a child until every sibling with a
     * smaller stageOrder is DONE. Null for the parent and for standalone batches.
     */
    @Column(name = "stage_order")
    private Integer stageOrder;

    public UUID getUuid() {
        if (this.pid == null) {
            return null;
        }
        if (!this.pid.startsWith("uuid:")) {
            throw new IllegalArgumentException("PID must start with 'uuid:'");
        }
        return UUID.fromString(this.pid.substring(5));
    }
}
