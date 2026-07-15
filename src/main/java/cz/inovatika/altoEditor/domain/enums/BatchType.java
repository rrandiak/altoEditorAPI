package cz.inovatika.altoEditor.domain.enums;

public enum BatchType {
    GENERATE_SINGLE,
    GENERATE_FOR_HIERARCHY,
    RETRIEVE_HIERARCHY,
    ACCEPT_VERSIONS,
    REINDEX,
    /**
     * Parent of a unified load→generate→accept pipeline. Never dispatched to a worker;
     * its child stage batches (linked via {@link cz.inovatika.altoEditor.domain.model.Batch#parentBatchId})
     * run in order and its state is derived by the pipeline coordinator.
     */
    PIPELINE,
}
