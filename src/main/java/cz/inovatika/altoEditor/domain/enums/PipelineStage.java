package cz.inovatika.altoEditor.domain.enums;

/**
 * A stage of the unified pipeline, in canonical execution order (enum declaration order).
 * Each maps to the batch type that actually runs the stage.
 */
public enum PipelineStage {
    RETRIEVE(BatchType.RETRIEVE_HIERARCHY),
    GENERATE(BatchType.GENERATE_FOR_HIERARCHY),
    ACCEPT(BatchType.ACCEPT_VERSIONS);

    private final BatchType batchType;

    PipelineStage(BatchType batchType) {
        this.batchType = batchType;
    }

    public BatchType getBatchType() {
        return batchType;
    }
}
