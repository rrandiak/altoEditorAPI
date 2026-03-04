package cz.inovatika.altoEditor.infrastructure.process;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

import cz.inovatika.altoEditor.config.properties.ApplicationProperties;
import cz.inovatika.altoEditor.domain.enums.BatchType;
import cz.inovatika.altoEditor.domain.model.Batch;
import cz.inovatika.altoEditor.domain.service.BatchService;
import cz.inovatika.altoEditor.infrastructure.process.templates.BatchProcess;
import lombok.RequiredArgsConstructor;

/**
 * Central dispatcher for batch processes. Holds per-type executors. The cron-driven
 * {@link #plan()} claims the oldest PLANNED batch per type (when capacity allows) and
 * runs it on the type's executor. Facades only create batches (PLANNED); no direct submit.
 */
@Component
@RequiredArgsConstructor
public class ProcessDispatcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessDispatcher.class);

    private final ApplicationProperties config;
    private final BatchService batchService;
    private final BatchProcessFactory processFactory;

    private final Map<BatchType, ThreadPoolExecutor> executorsByType = new EnumMap<>(BatchType.class);

    @PostConstruct
    public void initExecutors() {
        Thread.UncaughtExceptionHandler uncaughtHandler =
                (thread, ex) -> LOGGER.error("Uncaught in {}", thread.getName(), ex);
        for (BatchType type : BatchType.values()) {
            int max = Math.max(1, config.getMaxProcessesForType(type));
            BatchType typeForLambda = type;
            ThreadPoolExecutor executor = new ThreadPoolExecutor(
                    max,
                    max,
                    0L,
                    TimeUnit.MILLISECONDS,
                    new PriorityBlockingQueue<>(),
                    r -> {
                        Thread t = new Thread(r);
                        t.setName("ProcessDispatcher-" + typeForLambda.name() + "-" + t.threadId());
                        t.setUncaughtExceptionHandler(uncaughtHandler);
                        return t;
                    });
            executorsByType.put(type, executor);
        }
    }

    /** Number of tasks currently running for the given type (from the executor). */
    public int getRunningCountForType(BatchType type) {
        ThreadPoolExecutor executor = executorsByType.get(type);
        return executor != null ? executor.getActiveCount() : 0;
    }

    /** Running + queued count for the given type (used to cap planned submissions). */
    private int getRunningAndQueuedCountForType(BatchType type) {
        ThreadPoolExecutor executor = executorsByType.get(type);
        if (executor == null) {
            return 0;
        }
        return executor.getActiveCount() + executor.getQueue().size();
    }

    /**
     * Called by cron: for each batch type, fill all free slots by claiming the oldest
     * PLANNED batches and submitting them to the type's executor, until no capacity or no planned left.
     */
    public void plan() {
        for (BatchType type : BatchType.values()) {
            ThreadPoolExecutor executor = executorsByType.get(type);
            if (executor == null) {
                continue;
            }
            int max = Math.max(1, config.getMaxProcessesForType(type));
            while (getRunningAndQueuedCountForType(type) < max) {
                Optional<Batch> batchOpt = batchService.claimOldestPlannedBatchByType(type);
                if (batchOpt.isEmpty()) {
                    break;
                }
                Batch batch = batchOpt.get();
                BatchProcess process = processFactory.create(batch);
                executor.execute(process);
                LOGGER.debug("Planned batch {} (type {})", batch.getId(), type);
            }
        }
    }

    public void shutdown() {
        executorsByType.values().forEach(ThreadPoolExecutor::shutdown);
    }
}
