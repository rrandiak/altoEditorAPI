package cz.inovatika.altoEditor;

import java.io.File;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import cz.inovatika.altoEditor.config.ApplicationConfig;
import cz.inovatika.altoEditor.core.repository.BatchRepository;
import jakarta.annotation.PostConstruct;

@Configuration
public class AppInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger(AppInitializer.class);

    @Autowired
    private ApplicationConfig applicationConfig;

    @Autowired
    private BatchRepository batchRepository;

    @Value("${altoeditor.home:${user.home}}")
    private String appHomePath;

    @PostConstruct
    public void init() {
        try {
            initStorage();
            batchRepository.setFailedForAllRunningBatches("Application restarted.");
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private void initStorage() throws IOException {
        File objectStore = new File(applicationConfig.getObjectStore().getPath());
        if (!objectStore.exists()) {
            objectStore.mkdirs();
        }

        File dataStreamStore = new File(applicationConfig.getDataStreamStore().getPath());
        if (!dataStreamStore.exists()) {
            dataStreamStore.mkdirs();
        }

        LOGGER.info("Akubra storage initialized.");
        // AkubraStorage.getInstance();
    }
}
