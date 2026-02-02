package cz.inovatika.altoEditor;

import java.io.File;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

import cz.inovatika.altoEditor.config.properties.EnginesProperties;
import cz.inovatika.altoEditor.config.properties.StoreProperties;
import cz.inovatika.altoEditor.domain.enums.SpecialUser;
import cz.inovatika.altoEditor.domain.model.User;
import cz.inovatika.altoEditor.domain.repository.BatchRepository;
import cz.inovatika.altoEditor.domain.repository.UserRepository;
import jakarta.annotation.PostConstruct;

@Configuration
public class AppInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger(AppInitializer.class);

    @Autowired
    private StoreProperties storeProperties;

    @Autowired
    private EnginesProperties enginesProperties;

    @Autowired
    private BatchRepository batchRepository;

    @Autowired
    private UserRepository userRepository;

    @PostConstruct
    public void init() {
        try {
            initStorage();
            initSpecialUsers();
            initEngineUsers();
            batchRepository.failAllRunningBatches("Application restarted.");
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private void initStorage() throws IOException {
        File objectStore = new File(storeProperties.getPath());
        if (!objectStore.exists()) {
            objectStore.mkdirs();
        }

        LOGGER.info("Akubra storage initialized.");
    }

    private void initSpecialUsers() {
        if (userRepository.findSpecialUser(SpecialUser.KRAMERIUS).isEmpty()) {
            userRepository.save(User.builder()
                    .uid(SpecialUser.KRAMERIUS.getUsername())
                    .username(SpecialUser.KRAMERIUS.getUsername())
                    .build());
        }
    }

    private void initEngineUsers() {
        enginesProperties.getEngines().forEach((name, config) -> {
            if (userRepository.findByUsername(name).isEmpty()) {
                userRepository.save(User.builder()
                        .uid("engine-" + name)
                        .username(name)
                        .isEngine(true)
                        .build());
            }
        });
    }
}
