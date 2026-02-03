package cz.inovatika.altoEditor;

import java.io.File;
import java.io.IOException;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.domain.Specification;

import cz.inovatika.altoEditor.config.properties.EnginesProperties;
import cz.inovatika.altoEditor.config.properties.KrameriusProperties;
import cz.inovatika.altoEditor.config.properties.StoreProperties;
import cz.inovatika.altoEditor.domain.model.User;
import cz.inovatika.altoEditor.domain.repository.BatchRepository;
import cz.inovatika.altoEditor.domain.repository.UserRepository;
import cz.inovatika.altoEditor.domain.repository.spec.UserSpecifications;
import jakarta.annotation.PostConstruct;

@Configuration
public class AppInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger(AppInitializer.class);

    @Autowired
    private StoreProperties storeProperties;

    @Autowired
    private KrameriusProperties krameriusProperties;

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
            initKrameriusUsers();
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

    private void initKrameriusUsers() {
        krameriusProperties.getKrameriusInstances().forEach((name, config) -> {
            Optional<User> existingUser = userRepository.findByUsername(name);
            if (existingUser.isPresent()) {
                User user = existingUser.get();
                if (!user.isKramerius()) {
                    throw new IllegalStateException(
                            "User '" + name + "' already exists but is not marked as kramerius user.");
                }
                user.setEnabled(true);
                userRepository.save(user);
            } else {
                userRepository.save(User.builder()
                        .uid("kramerius-" + name)
                        .username(name)
                        .isKramerius(true)
                        .build());
            }
        });
        userRepository.findAll(Specification.allOf(UserSpecifications.isKramerius(true))).stream()
                .filter(user -> !krameriusProperties.getKrameriusInstances().containsKey(user.getUsername()))
                .forEach(user -> {
                    user.setEnabled(false);
                    userRepository.save(user);
                    LOGGER.warn("Kramerius user '{}' disabled because it is no longer configured.", user.getUsername());
                });
    }

    private void initEngineUsers() {
        enginesProperties.getEngines().forEach((name, config) -> {
            Optional<User> existingUser = userRepository.findByUsername(name);
            if (existingUser.isPresent()) {
                User user = existingUser.get();
                if (!user.isEngine()) {
                    throw new IllegalStateException(
                            "User '" + name + "' already exists but is not marked as engine user.");
                }
                user.setEnabled(true);
                userRepository.save(user);
            } else {
                userRepository.save(User.builder()
                        .uid("engine-" + name)
                        .username(name)
                        .isEngine(true)
                        .build());
            }
        });
        userRepository.findAll(Specification.allOf(UserSpecifications.isEngine(true))).stream()
                .filter(user -> !enginesProperties.getEngines().containsKey(user.getUsername()))
                .forEach(user -> {
                    user.setEnabled(false);
                    userRepository.save(user);
                    LOGGER.warn("Engine user '{}' disabled because it is no longer configured.", user.getUsername());
                });
    }
}
