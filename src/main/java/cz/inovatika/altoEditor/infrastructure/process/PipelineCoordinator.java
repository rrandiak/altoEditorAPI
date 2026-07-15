package cz.inovatika.altoEditor.infrastructure.process;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import cz.inovatika.altoEditor.domain.enums.BatchState;
import cz.inovatika.altoEditor.domain.enums.BatchType;
import cz.inovatika.altoEditor.domain.model.Batch;
import cz.inovatika.altoEditor.domain.repository.BatchRepository;
import cz.inovatika.altoEditor.domain.service.BatchService;
import lombok.RequiredArgsConstructor;

/**
 * Derives the state of {@link BatchType#PIPELINE} parent batches from their child stage
 * batches, and enforces fail-fast propagation. Run each scheduler tick.
 *
 * <p>Because the dependency-aware claim only starts a stage once its predecessor is DONE, a
 * failed stage would otherwise leave downstream children PLANNED forever — the coordinator
 * fails them and the parent.
 */
@Component
@RequiredArgsConstructor
public class PipelineCoordinator {

    private static final Logger LOGGER = LoggerFactory.getLogger(PipelineCoordinator.class);

    private final BatchRepository batchRepository;
    private final BatchService batchService;

    /** Reconcile every non-terminal pipeline: propagate failure, or roll up to RUNNING/DONE. */
    @Transactional
    public void reconcile() {
        List<Batch> pipelines = batchRepository.findByTypeAndStateIn(
                BatchType.PIPELINE, List.of(BatchState.PLANNED, BatchState.RUNNING));

        for (Batch pipeline : pipelines) {
            reconcilePipeline(pipeline);
        }
    }

    private void reconcilePipeline(Batch pipeline) {
        List<Batch> children = batchRepository.findByParentBatchIdOrderByStageOrderAsc(pipeline.getId());
        if (children.isEmpty()) {
            return;
        }

        Batch failed = children.stream()
                .filter(c -> c.getState() == BatchState.FAILED)
                .findFirst()
                .orElse(null);

        if (failed != null) {
            for (Batch child : children) {
                if (child.getState() == BatchState.PLANNED) {
                    batchService.setFailed(child, "Skipped: earlier pipeline stage failed");
                }
            }
            batchService.setFailed(pipeline,
                    "Pipeline failed at stage " + failed.getType() + " (batch " + failed.getId() + ")");
            LOGGER.debug("Pipeline {} failed at stage {} (batch {})",
                    pipeline.getId(), failed.getType(), failed.getId());
            return;
        }

        boolean allDone = children.stream().allMatch(c -> c.getState() == BatchState.DONE);
        if (allDone) {
            batchService.setState(pipeline, BatchState.DONE);
            LOGGER.debug("Pipeline {} completed", pipeline.getId());
            return;
        }

        boolean anyStarted = children.stream()
                .anyMatch(c -> c.getState() == BatchState.RUNNING || c.getState() == BatchState.DONE);
        if (anyStarted && pipeline.getState() != BatchState.RUNNING) {
            batchService.setState(pipeline, BatchState.RUNNING);
        }
    }
}
