package cz.inovatika.altoEditor.infrastructure.process;

import cz.inovatika.altoEditor.domain.model.Batch;
import cz.inovatika.altoEditor.infrastructure.process.templates.BatchProcess;

/**
 * Creates a {@link BatchProcess} for a given batch. Used by the dispatcher for both
 * user-triggered and cron-planned execution.
 */
public interface BatchProcessFactory {

    /**
     * Create a runnable process for the given batch. The batch must be in RUNNING state
     * when dispatched by the planner (claimed); user-triggered batches are submitted
     * immediately after creation (still PLANNED) and the process will set RUNNING on start.
     */
    BatchProcess create(Batch batch);
}
