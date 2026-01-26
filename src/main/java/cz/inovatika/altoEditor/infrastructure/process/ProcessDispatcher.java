package cz.inovatika.altoEditor.infrastructure.process;

import java.util.concurrent.Future;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import cz.inovatika.altoEditor.config.properties.ApplicationProperties;
import cz.inovatika.altoEditor.infrastructure.process.templates.BatchProcess;

@Component
public class ProcessDispatcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessDispatcher.class);

    private final ThreadPoolExecutor executor;

    public ProcessDispatcher(ApplicationProperties config) {
        this.executor = new ThreadPoolExecutor(
                config.getMaxProcesses(),
                config.getMaxProcesses(),
                0L,
                TimeUnit.MILLISECONDS,
                new PriorityBlockingQueue<>(),
                r -> {
                    Thread t = new Thread(r);
                    t.setName("ProcessDispatcher-" + t.threadId());
                    t.setUncaughtExceptionHandler(
                            (thread, ex) -> LOGGER.error("Uncaught in " + thread.getName(), ex));
                    return t;
                });
    }

    public <T extends BatchProcess> Future<T> submit(T process) {
        return executor.submit(process, process);
    }

    public void shutdown() {
        executor.shutdown();
    }
}
