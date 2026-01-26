package cz.inovatika.altoEditor.domain.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import cz.inovatika.altoEditor.domain.enums.BatchState;
import cz.inovatika.altoEditor.domain.model.Batch;
import jakarta.transaction.Transactional;

@Repository
public interface BatchRepository extends JpaRepository<Batch, Integer>,
        JpaSpecificationExecutor<Batch> {

    /**
     * Find batches by state, ordered by ID ascending
     */
    List<Batch> findByStateOrderByIdAsc(BatchState state);

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
