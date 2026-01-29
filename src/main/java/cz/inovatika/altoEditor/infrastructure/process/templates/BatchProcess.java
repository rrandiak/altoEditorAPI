package cz.inovatika.altoEditor.infrastructure.process.templates;

import java.time.LocalDateTime;

import cz.inovatika.altoEditor.domain.enums.BatchPriority;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public abstract class BatchProcess implements Runnable, Comparable<BatchProcess> {

    protected final Integer batchId;
    protected final BatchPriority priority;
    protected final LocalDateTime createdAt;

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
        return this.getCreatedAt().compareTo(other.getCreatedAt());
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
