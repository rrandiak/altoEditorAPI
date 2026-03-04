package cz.inovatika.altoEditor.infrastructure.process;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * Triggers {@link ProcessDispatcher#plan()} on a schedule so PLANNED batches
 * are picked up and submitted to executors. Disable with {@code application.process-scheduling.enabled=false}.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "application.process-scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class ProcessScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessScheduler.class);

    private final ProcessDispatcher processDispatcher;

    /** Run every 5 seconds. Override with {@code application.process-scheduling.cron}. */
    @Scheduled(cron = "${application.process-scheduling.cron:0/5 * * * * ?}")
    public void planBatches() {
        LOGGER.trace("Running batch plan");
        processDispatcher.plan();
    }
}
