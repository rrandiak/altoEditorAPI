package cz.inovatika.altoEditor.infrastructure.process.reindex;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import cz.inovatika.altoEditor.domain.model.Batch;
import cz.inovatika.altoEditor.domain.service.BatchService;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ReindexProcessFactory {

    private final BatchService batchService;
    private final EntityManager entityManager;
    private final PlatformTransactionManager transactionManager;

    public ReindexProcess create(Batch batch) {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        return new ReindexProcess(batchService, entityManager, transactionTemplate, batch);
    }
}
