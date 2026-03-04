package cz.inovatika.altoEditor.infrastructure.process;

import org.springframework.stereotype.Component;

import cz.inovatika.altoEditor.domain.model.Batch;
import cz.inovatika.altoEditor.infrastructure.process.accept.AcceptEngineVersionsProcessFactory;
import cz.inovatika.altoEditor.infrastructure.process.altoocr.AltoOcrGeneratorProcessFactory;
import cz.inovatika.altoEditor.infrastructure.process.reindex.ReindexProcessFactory;
import cz.inovatika.altoEditor.infrastructure.process.retrieve.RetrieveHierarchyProcessFactory;
import cz.inovatika.altoEditor.infrastructure.process.templates.BatchProcess;
import lombok.RequiredArgsConstructor;

/**
 * Registry that creates the appropriate {@link BatchProcess} for a batch by type.
 * Used by {@link ProcessDispatcher} when the scheduler runs {@link ProcessDispatcher#plan()}.
 */
@Component
@RequiredArgsConstructor
public class CompositeBatchProcessFactory implements BatchProcessFactory {

    private final AltoOcrGeneratorProcessFactory altoOcrGeneratorProcessFactory;
    private final RetrieveHierarchyProcessFactory retrieveHierarchyProcessFactory;
    private final AcceptEngineVersionsProcessFactory acceptEngineVersionsProcessFactory;
    private final ReindexProcessFactory reindexProcessFactory;

    @Override
    public BatchProcess create(Batch batch) {
        return switch (batch.getType()) {
            case GENERATE_SINGLE, GENERATE_FOR_HIERARCHY -> altoOcrGeneratorProcessFactory.create(batch);
            case RETRIEVE_HIERARCHY -> retrieveHierarchyProcessFactory.create(batch);
            case ACCEPT_ENGINE_VERSIONS -> acceptEngineVersionsProcessFactory.create(batch);
            case REINDEX -> reindexProcessFactory.create(batch);
        };
    }
}
