package cz.inovatika.altoEditor.domain.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import cz.inovatika.altoEditor.domain.enums.BatchState;
import cz.inovatika.altoEditor.domain.enums.BatchType;
import cz.inovatika.altoEditor.domain.model.Batch;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;

@Repository
public interface BatchRepository extends JpaRepository<Batch, Integer>,
        JpaSpecificationExecutor<Batch> {

    /**
     * Find batches with createdBy fetched in one query (avoids N+1 when mapping to DTO).
     */
    @EntityGraph(attributePaths = "createdBy")
    @Override
    Page<Batch> findAll(Specification<Batch> spec, Pageable pageable);

    /**
     * Find batches by state, ordered by ID ascending
     */
    List<Batch> findByStateOrderByIdAsc(BatchState state);

    /**
     * Find the oldest claimable batch of the given state and type (for planning), locked to
     * avoid double-claim. A pipeline child stage is claimable only once every sibling with a
     * smaller stageOrder is DONE; standalone batches (no parent) are always claimable.
     * Pass a page request of size 1 to get just the oldest.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
                SELECT b FROM Batch b
                WHERE b.state = :state AND b.type = :type
                  AND (b.parentBatchId IS NULL
                       OR NOT EXISTS (
                           SELECT s FROM Batch s
                           WHERE s.parentBatchId = b.parentBatchId
                             AND s.stageOrder < b.stageOrder
                             AND s.state <> BatchState.DONE
                       ))
                ORDER BY b.id ASC
            """)
    List<Batch> findClaimableByStateAndType(@Param("state") BatchState state,
            @Param("type") BatchType type, Pageable pageable);

    /** Child stage batches of a pipeline, ordered by their stage position. */
    List<Batch> findByParentBatchIdOrderByStageOrderAsc(Integer parentBatchId);

    /** Batches of a type in any of the given states (e.g. non-terminal pipelines to reconcile). */
    List<Batch> findByTypeAndStateIn(BatchType type, Collection<BatchState> states);

    /**
     * Set all RUNNING batches to FAILED with a log message
     * Used for cleanup when application starts
     */
    @Modifying
    @Transactional
    @Query("""
                UPDATE Batch b
                SET b.state = BatchState.FAILED,
                    b.log = CONCAT(COALESCE(b.log, ''), :log)
                WHERE b.state = BatchState.RUNNING
            """)
    int failAllRunningBatches(@Param("log") String log);
}
