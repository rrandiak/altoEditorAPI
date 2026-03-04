package cz.inovatika.altoEditor.domain.repository;

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
     * Find oldest batch by state and type (for planning). Locked to avoid double-claim.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Batch> findFirstByStateAndTypeOrderByIdAsc(BatchState state, BatchType type);

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
