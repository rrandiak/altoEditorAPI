package cz.inovatika.altoEditor;

import java.io.File;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

import cz.inovatika.altoEditor.config.properties.ProcessorsProperties;
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
    private ProcessorsProperties processorsProperties;

    @Autowired
    private BatchRepository batchRepository;

    @Autowired
    private UserRepository userRepository;

    @PostConstruct
    public void init() {
        try {
            initStorage();
            initAltoeditorUsers();
            initProcessorUsers();
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

    private void initAltoeditorUsers() {
        if (userRepository.findSpecialUser(SpecialUser.PERO).isEmpty()) {
            userRepository.save(User.builder()
                    .login(SpecialUser.PERO.name())
                    .build());
        }
        if (userRepository.findSpecialUser(SpecialUser.ALTOEDITOR).isEmpty()) {
            userRepository.save(User.builder()
                    .login(SpecialUser.ALTOEDITOR.name())
                    .build());
        }
    }

    private void initProcessorUsers() {
        processorsProperties.getProcessors().forEach((name, config) -> {
            if (userRepository.findByLogin(name).isEmpty()) {
                userRepository.save(User.builder()
                        .login(name)
                        .build());
            }
        });
    }
}
