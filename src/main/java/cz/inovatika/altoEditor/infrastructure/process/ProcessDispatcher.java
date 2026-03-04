package cz.inovatika.altoEditor.infrastructure.process;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import cz.inovatika.altoEditor.config.properties.ApplicationProperties;
import cz.inovatika.altoEditor.domain.enums.BatchType;
import cz.inovatika.altoEditor.infrastructure.process.templates.BatchProcess;

@Component
public class ProcessDispatcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessDispatcher.class);

    private final Map<BatchType, ThreadPoolExecutor> executorsByType = new EnumMap<>(BatchType.class);

    public ProcessDispatcher(ApplicationProperties config) {
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

    public void submit(BatchProcess process) {
        ThreadPoolExecutor executor = executorsByType.get(process.getType());
        if (executor == null) {
            throw new IllegalArgumentException("No executor for batch type: " + process.getType());
        }
        executor.execute(process);
    }

    public void shutdown() {
        executorsByType.values().forEach(ThreadPoolExecutor::shutdown);
    }
}
