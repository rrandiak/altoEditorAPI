package cz.inovatika.altoEditor.domain.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.TestPropertySource;

import cz.inovatika.altoEditor.domain.enums.AltoVersionState;
import cz.inovatika.altoEditor.domain.enums.SpecialUser;
import cz.inovatika.altoEditor.domain.model.AltoVersion;
import cz.inovatika.altoEditor.domain.model.DigitalObject;
import cz.inovatika.altoEditor.domain.model.User;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@TestPropertySource(properties = "altoeditor.home=src/test/resources")
class AltoVersionRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private AltoVersionRepository repository;

    @Autowired
    private DigitalObjectRepository digitalObjectRepository;

    @Autowired
    private UserRepository userRepository;

    // Test user IDs
    private User ALTO_EDITOR_USER;
    private User PERO_USER;
    private User CURRENT_USER;

    // Test constants
    private static final UUID TEST_UUID = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
    private static final String TEST_PID = "uuid:" + TEST_UUID.toString();
    private static final UUID TEST_UUID_2 = UUID.fromString("223e4567-e89b-12d3-a456-426614174000");
    private static final String TEST_PID_2 = "uuid:" + TEST_UUID_2.toString();
    private static final String TEST_INSTANCE = "dk";
    private static final int VERSION_1 = 1;
    private static final int VERSION_2 = 2;

    private AltoVersion createAltoVersion(String pid, User user, String instanceId,
            AltoVersionState state, int version) {

        DigitalObject dobj = digitalObjectRepository.save(DigitalObject.builder().pid(pid).build());

        return AltoVersion.builder()
                .digitalObject(dobj)
                .user(user)
                .instance(instanceId)
                .state(state)
                .version(version)
                .build();
    }

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();

        ALTO_EDITOR_USER = userRepository.save(User.builder().username(SpecialUser.KRAMERIUS.getUsername()).build());
        PERO_USER = userRepository.save(User.builder().username("pero").isEngine(true).build());
        CURRENT_USER = userRepository.save(User.builder().username("current_user").build());

        repository.deleteAll();
    }

    @Test
    @DisplayName("When saving digital object, then date is automatically set")
    void whenSaveAltoVersion_thenDateIsSet() {
        // Given
        AltoVersion obj = createAltoVersion(TEST_PID, CURRENT_USER, TEST_INSTANCE,
                AltoVersionState.ACTIVE, VERSION_1);

        // When
        AltoVersion saved = repository.save(obj);
        entityManager.flush();

        // Then
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getCreatedAt()).isBeforeOrEqualTo(LocalDateTime.now());
    }

    @Test
    @DisplayName("Find all by PID should return all objects with that PID")
    void findByPid_shouldReturnAllObjects() {
        // Given
        repository.save(createAltoVersion(TEST_PID, CURRENT_USER, TEST_INSTANCE,
                AltoVersionState.ACTIVE, VERSION_1));
        repository.save(createAltoVersion(TEST_PID, PERO_USER, TEST_INSTANCE,
                AltoVersionState.ACTIVE, VERSION_2));
        repository.save(createAltoVersion(TEST_PID_2, CURRENT_USER, TEST_INSTANCE,
                AltoVersionState.ACTIVE, VERSION_1));
        entityManager.flush();

        // When
        List<AltoVersion> result = repository.findAllByDigitalObjectUuid(TEST_UUID);

        // Then
        assertThat(result).hasSize(2);
        assertThat(result).allMatch(obj -> obj.getDigitalObject().getUuid().equals(TEST_UUID));
    }

    @Test
    @DisplayName("Exists by PID should return true when object exists")
    void existsByPid_shouldReturnTrue_whenExists() {
        // Given
        repository.save(createAltoVersion(TEST_PID, CURRENT_USER, TEST_INSTANCE,
                AltoVersionState.ACTIVE, VERSION_1));
        entityManager.flush();

        // When
        boolean exists = repository.existsByDigitalObjectUuid(TEST_UUID);

        // Then
        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("Exists by PID should return false when object does not exist")
    void existsByPid_shouldReturnFalse_whenNotExists() {
        // When
        boolean exists = repository.existsByDigitalObjectUuid(UUID.fromString("123e4567-e89b-12d3-a456-426614174999"));

        // Then
        assertThat(exists).isFalse();
    }

    @Test
    @DisplayName("Find by PID and instance should return correct object")
    void findByPidAndInstance_shouldReturnObject() {
        // Given
        repository.save(createAltoVersion(TEST_PID, CURRENT_USER, "dk",
                AltoVersionState.ACTIVE, VERSION_1));
        repository.save(createAltoVersion(TEST_PID, CURRENT_USER, "mzk",
                AltoVersionState.ACTIVE, VERSION_2));
        entityManager.flush();

        // When
        Optional<AltoVersion> result = repository.findFirstByDigitalObjectUuidAndInstance(TEST_UUID, "dk");

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getInstance()).isEqualTo("dk");
    }

    @Test
    @DisplayName("Find by PID and user ID should return user's object")
    void findByPidAndUserId_shouldReturnUsersObject() {
        // Given
        repository.save(createAltoVersion(TEST_PID, CURRENT_USER, TEST_INSTANCE,
                AltoVersionState.ACTIVE, VERSION_1));
        repository.save(createAltoVersion(TEST_PID, PERO_USER, TEST_INSTANCE,
                AltoVersionState.ACTIVE, VERSION_2));
        entityManager.flush();

        // When
        Optional<AltoVersion> result = repository.findByDigitalObjectUuidAndUserId(TEST_UUID, CURRENT_USER.getId());

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getUser().getId()).isEqualTo(CURRENT_USER.getId());
    }

    @Test
    @DisplayName("Find first by PID ordered by version desc should return max version object")
    void findFirstByPidOrderByVersionDesc_shouldReturnMaxVersionObjects() {
        // Given
        repository.save(createAltoVersion(TEST_PID, ALTO_EDITOR_USER, TEST_INSTANCE,
                AltoVersionState.ACTIVE, 3));
        repository.save(createAltoVersion(TEST_PID, CURRENT_USER, TEST_INSTANCE,
                AltoVersionState.ARCHIVED, 1));
        repository.save(createAltoVersion(TEST_PID, PERO_USER, TEST_INSTANCE,
                AltoVersionState.ARCHIVED, 2));
        entityManager.flush();

        // When
        Optional<AltoVersion> result = repository.findFirstByDigitalObjectUuidOrderByVersionDesc(TEST_UUID);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getVersion()).isEqualTo(3);
    }

    @Test
    @DisplayName("Find by PID and version should return correct object")
    void findByPidAndVersion_shouldReturnCorrectObject() {
        // Given
        repository.save(createAltoVersion(TEST_PID, CURRENT_USER, TEST_INSTANCE,
                AltoVersionState.ACTIVE, VERSION_1));
        repository.save(createAltoVersion(TEST_PID, CURRENT_USER, TEST_INSTANCE,
                AltoVersionState.ACTIVE, VERSION_2));
        entityManager.flush();

        // When
        Optional<AltoVersion> result = repository.findByDigitalObjectUuidAndVersion(TEST_UUID, VERSION_2);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getVersion()).isEqualTo(VERSION_2);
    }

    @Test
    @DisplayName("findRelated should prioritize user over ACTIVE state")
    void findRelated_shouldPrioritizeUserOverActive() {
        // Given
        // User's object (should be prioritized)
        AltoVersion userObj = createAltoVersion(TEST_PID, CURRENT_USER, TEST_INSTANCE, AltoVersionState.ARCHIVED,
                VERSION_1);
        // ACTIVE object (should be second)
        AltoVersion activeObj = createAltoVersion(TEST_PID, PERO_USER, TEST_INSTANCE, AltoVersionState.ACTIVE,
                VERSION_2);
        repository.save(userObj);
        repository.save(activeObj);
        entityManager.flush();

        // When
        Optional<AltoVersion> result = repository.findRelated(TEST_UUID, CURRENT_USER.getId());

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getUser().getId()).isEqualTo(CURRENT_USER.getId());
        assertThat(result.get().getState()).isEqualTo(AltoVersionState.ARCHIVED);
    }

    @Test
    @DisplayName("findRelated should return ACTIVE if user object not present")
    void findRelated_shouldReturnActiveIfNoUserObject() {
        // Given
        AltoVersion activeObj = createAltoVersion(TEST_PID, PERO_USER, TEST_INSTANCE, AltoVersionState.ACTIVE,
                VERSION_2);
        repository.save(activeObj);
        entityManager.flush();

        // When
        Optional<AltoVersion> result = repository.findRelated(TEST_UUID, CURRENT_USER.getId());

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getState()).isEqualTo(AltoVersionState.ACTIVE);
    }

    @Test
    @DisplayName("Find by PID and instance ID should return correct object")
    void findByPidAndInstanceId_shouldReturnCorrectObject() {
        // Given
        repository.save(createAltoVersion(TEST_PID, CURRENT_USER, "inst1",
                AltoVersionState.ACTIVE, VERSION_1));
        repository.save(createAltoVersion(TEST_PID, CURRENT_USER, "inst2",
                AltoVersionState.ACTIVE, VERSION_2));
        entityManager.flush();

        // When
        Optional<AltoVersion> result = repository.findFirstByDigitalObjectUuidAndInstance(TEST_UUID, "inst2");

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getInstance()).isEqualTo("inst2");
    }
}