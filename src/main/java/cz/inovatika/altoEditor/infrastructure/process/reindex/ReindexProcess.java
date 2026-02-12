package cz.inovatika.altoEditor.infrastructure.process.reindex;

import org.hibernate.search.mapper.orm.Search;
import org.hibernate.search.mapper.orm.session.SearchSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionTemplate;

import cz.inovatika.altoEditor.domain.enums.BatchState;
import cz.inovatika.altoEditor.domain.model.AltoVersion;
import cz.inovatika.altoEditor.domain.model.Batch;
import cz.inovatika.altoEditor.domain.model.DigitalObject;
import cz.inovatika.altoEditor.domain.service.BatchService;
import cz.inovatika.altoEditor.infrastructure.process.templates.BatchProcess;
import jakarta.persistence.EntityManager;

/**
 * Batch process that purges and reindexes both DigitalObject and AltoVersion
 * search indexes. Runs reindex logic inside a transaction so the worker thread
 * has a transactional EntityManager.
 */
public class ReindexProcess extends BatchProcess {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReindexProcess.class);

    private final BatchService batchService;
    private final EntityManager entityManager;
    private final TransactionTemplate transactionTemplate;

    public ReindexProcess(
            BatchService batchService,
            EntityManager entityManager,
            TransactionTemplate transactionTemplate,
            Batch batch) {
        super(batch.getId(), batch.getPriority(), batch.getCreatedAt());
        this.batchService = batchService;
        this.entityManager = entityManager;
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public void run() {
        Batch batch = batchService.getById(batchId);
        try {
            batchService.setState(batch, BatchState.RUNNING);

            transactionTemplate.executeWithoutResult(status -> {
                SearchSession session = Search.session(entityManager);
                LOGGER.info("Reindex batch {}: purging and reindexing DigitalObject index", batchId);
                try {
                    session.massIndexer(DigitalObject.class)
                            .purgeAllOnStart(true)
                            .startAndWait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Reindex interrupted", e);
                }

                LOGGER.info("Reindex batch {}: purging and reindexing AltoVersion index", batchId);
                try {
                    session.massIndexer(AltoVersion.class)
                            .purgeAllOnStart(true)
                            .startAndWait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Reindex interrupted", e);
                }
            });

            batchService.setState(batch, BatchState.DONE);
            LOGGER.info("Reindex batch {}: done", batchId);
        } catch (Exception ex) {
            LOGGER.error("Reindex batch {} failed: {}", batchId, ex.getMessage(), ex);
            try {
                batchService.setFailed(batch, "Reindex failed: " + ex.getMessage());
            } catch (Exception e) {
                LOGGER.error("Failed to set batch as failed: {}", e.getMessage(), e);
            }
        }
    }
}
