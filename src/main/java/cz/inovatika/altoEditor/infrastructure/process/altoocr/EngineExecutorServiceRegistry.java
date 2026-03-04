package cz.inovatika.altoEditor.infrastructure.process.altoocr;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

import cz.inovatika.altoEditor.config.properties.EnginesProperties;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class EngineExecutorServiceRegistry implements DisposableBean {

    private final ConcurrentHashMap<String, ExecutorService> pools = new ConcurrentHashMap<>();
    private final EnginesProperties enginesProperties;

    public ExecutorService getOrCreate(String engineName) {
        return pools.computeIfAbsent(engineName, name -> {
            int parallelism = enginesProperties.getEngineConfig(name).getParallelism();
            ThreadFactory factory = r -> {
                Thread t = new Thread(r);
                t.setName("alto-engine-" + name + "-" + t.threadId());
                return t;
            };
            return Executors.newFixedThreadPool(parallelism, factory);
        });
    }

    @Override
    public void destroy() {
        pools.values().forEach(ExecutorService::shutdown);
    }
}
