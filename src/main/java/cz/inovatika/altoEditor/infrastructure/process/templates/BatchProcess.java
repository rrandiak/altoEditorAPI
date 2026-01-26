package cz.inovatika.altoEditor.infrastructure.process.templates;

import java.time.LocalDateTime;

import cz.inovatika.altoEditor.domain.enums.BatchPriority;
import cz.inovatika.altoEditor.domain.model.Batch;

public abstract class BatchProcess implements Runnable, Comparable<BatchProcess> {

    protected final Batch batch;

    protected BatchProcess(Batch batch) {
        this.batch = batch;
    }

    public Batch getBatch() {
        return batch;
    }

    public BatchPriority getPriority() {
        return batch.getPriority();
    }

    public LocalDateTime getCreatedDate() {
        return batch.getCreateDate();
    }

    @Override
    public final int compareTo(BatchProcess other) {
        if (this == other) {
            return 0;
        }

        // higher priority first
        int priorityCompare = Integer.compare(
                priorityValue(other.getPriority()),
                priorityValue(this.getPriority()));
        if (priorityCompare != 0) {
            return priorityCompare;
        }

        // older batch first
        return this.getCreatedDate().compareTo(other.getCreatedDate());
    }

    private static int priorityValue(BatchPriority p) {
        if (p == null) {
            return 0;
        }
        return switch (p) {
            case HIGH -> 1;
            case MEDIUM -> 0;
            case LOW -> -1;
        };
    }
}
