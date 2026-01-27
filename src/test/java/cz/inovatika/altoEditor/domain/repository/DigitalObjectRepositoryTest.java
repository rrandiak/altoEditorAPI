package cz.inovatika.altoEditor.domain.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.TestPropertySource;

import cz.inovatika.altoEditor.domain.enums.DigitalObjectState;
import cz.inovatika.altoEditor.domain.enums.SpecialUser;
import cz.inovatika.altoEditor.domain.model.DigitalObject;
import cz.inovatika.altoEditor.domain.model.User;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@TestPropertySource(properties = "altoeditor.home=src/test/resources")
class DigitalObjectRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private DigitalObjectRepository repository;

    @Autowired
    private UserRepository userRepository;

    // Test user IDs
    private User ALTO_EDITOR_USER;
    private User PERO_USER;
    private User CURRENT_USER;

    // Test constants
    private static final String TEST_PID = "uuid:12345";
    private static final String TEST_INSTANCE = "dk";
    private static final int VERSION_1 = 1;
    private static final int VERSION_2 = 2;

    private DigitalObject createDigitalObject(String pid, User user, String instanceId,
            DigitalObjectState state, int version) {
        return DigitalObject.builder()
                .pid(pid)
                .user(user)
                .instanceId(instanceId)
                .state(state)
                .version(version)
                .label("Test Label")
                .build();
    }

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();

        ALTO_EDITOR_USER = userRepository.save(User.builder().username(SpecialUser.ALTOEDITOR.getUsername()).build());
        PERO_USER = userRepository.save(User.builder().username("pero").isEngine(true).build());
        CURRENT_USER = userRepository.save(User.builder().username("current_user").build());

        repository.deleteAll();
    }

    @Test
    @DisplayName("When saving digital object, then date is automatically set")
    void whenSaveDigitalObject_thenDateIsSet() {
        // Given
        DigitalObject obj = createDigitalObject(TEST_PID, CURRENT_USER, TEST_INSTANCE,
                DigitalObjectState.ACTIVE, VERSION_1);

        // When
        DigitalObject saved = repository.save(obj);
        entityManager.flush();

        // Then
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getDate()).isNotNull();
        assertThat(saved.getDate()).isBeforeOrEqualTo(LocalDateTime.now());
    }

    @Test
    @DisplayName("Find all by PID should return all objects with that PID")
    void findByPid_shouldReturnAllObjects() {
        // Given
        repository.save(createDigitalObject(TEST_PID, CURRENT_USER, TEST_INSTANCE,
                DigitalObjectState.ACTIVE, VERSION_1));
        repository.save(createDigitalObject(TEST_PID, PERO_USER, TEST_INSTANCE,
                DigitalObjectState.ACTIVE, VERSION_2));
        repository.save(createDigitalObject("uuid:other", CURRENT_USER, TEST_INSTANCE,
                DigitalObjectState.ACTIVE, VERSION_1));
        entityManager.flush();

        // When
        List<DigitalObject> result = repository.findAllByPid(TEST_PID);

        // Then
        assertThat(result).hasSize(2);
        assertThat(result).allMatch(obj -> obj.getPid().equals(TEST_PID));
    }

    @Test
    @DisplayName("Exists by PID should return true when object exists")
    void existsByPid_shouldReturnTrue_whenExists() {
        // Given
        repository.save(createDigitalObject(TEST_PID, CURRENT_USER, TEST_INSTANCE,
                DigitalObjectState.ACTIVE, VERSION_1));
        entityManager.flush();

        // When
        boolean exists = repository.existsByPid(TEST_PID);

        // Then
        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("Exists by PID should return false when object does not exist")
    void existsByPid_shouldReturnFalse_whenNotExists() {
        // When
        boolean exists = repository.existsByPid("uuid:nonexistent");

        // Then
        assertThat(exists).isFalse();
    }

    @Test
    @DisplayName("Find by PID and instance should return correct object")
    void findByPidAndInstance_shouldReturnObject() {
        // Given
        repository.save(createDigitalObject(TEST_PID, CURRENT_USER, "dk",
                DigitalObjectState.ACTIVE, VERSION_1));
        repository.save(createDigitalObject(TEST_PID, CURRENT_USER, "mzk",
                DigitalObjectState.ACTIVE, VERSION_1));
        entityManager.flush();

        // When
        Optional<DigitalObject> result = repository.findByPidAndInstanceId(TEST_PID, "dk");

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getInstanceId()).isEqualTo("dk");
    }

    @Test
    @DisplayName("Find by PID and user ID should return user's object")
    void findByPidAndUserId_shouldReturnUsersObject() {
        // Given
        repository.save(createDigitalObject(TEST_PID, CURRENT_USER, TEST_INSTANCE,
                DigitalObjectState.ACTIVE, VERSION_1));
        repository.save(createDigitalObject(TEST_PID, PERO_USER, TEST_INSTANCE,
                DigitalObjectState.ACTIVE, VERSION_2));
        entityManager.flush();

        // When
        Optional<DigitalObject> result = repository.findByPidAndUserId(TEST_PID, CURRENT_USER.getId());

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getUser().getId()).isEqualTo(CURRENT_USER.getId());
    }

    @Test
    @DisplayName("Find first by PID ordered by version desc should return max version object")
    void findFirstByPidOrderByVersionDesc_shouldReturnMaxVersionObjects() {
        // Given
        repository.save(createDigitalObject(TEST_PID, ALTO_EDITOR_USER, TEST_INSTANCE,
                DigitalObjectState.ACTIVE, 3));
        repository.save(createDigitalObject(TEST_PID, CURRENT_USER, TEST_INSTANCE,
                DigitalObjectState.ARCHIVED, 1));
        repository.save(createDigitalObject(TEST_PID, PERO_USER, TEST_INSTANCE,
                DigitalObjectState.ARCHIVED, 2));
        entityManager.flush();

        // When
        Optional<DigitalObject> result = repository.findFirstByPidOrderByVersionDesc(TEST_PID);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getVersion()).isEqualTo(3);
    }

    @Test
    @DisplayName("Find by PID and version should return correct object")
    void findByPidAndVersion_shouldReturnCorrectObject() {
        // Given
        repository.save(createDigitalObject(TEST_PID, CURRENT_USER, TEST_INSTANCE,
                DigitalObjectState.ACTIVE, VERSION_1));
        repository.save(createDigitalObject(TEST_PID, CURRENT_USER, TEST_INSTANCE,
                DigitalObjectState.ACTIVE, VERSION_2));
        entityManager.flush();

        // When
        Optional<DigitalObject> result = repository.findByPidAndVersion(TEST_PID, VERSION_2);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getVersion()).isEqualTo(VERSION_2);
    }

        @Test
        @DisplayName("findRelated should prioritize user over ACTIVE state")
        void findRelated_shouldPrioritizeUserOverActive() {
                // Given
                // User's object (should be prioritized)
                DigitalObject userObj = createDigitalObject(TEST_PID, CURRENT_USER, TEST_INSTANCE, DigitalObjectState.ARCHIVED, VERSION_1);
                // ACTIVE object (should be second)
                DigitalObject activeObj = createDigitalObject(TEST_PID, PERO_USER, TEST_INSTANCE, DigitalObjectState.ACTIVE, VERSION_2);
                repository.save(userObj);
                repository.save(activeObj);
                entityManager.flush();

                // When
                Optional<DigitalObject> result = repository.findRelated(TEST_PID, CURRENT_USER.getId());

                // Then
                assertThat(result).isPresent();
                assertThat(result.get().getUser().getId()).isEqualTo(CURRENT_USER.getId());
                assertThat(result.get().getState()).isEqualTo(DigitalObjectState.ARCHIVED);
        }

        @Test
        @DisplayName("findRelated should return ACTIVE if user object not present")
        void findRelated_shouldReturnActiveIfNoUserObject() {
                // Given
                DigitalObject activeObj = createDigitalObject(TEST_PID, PERO_USER, TEST_INSTANCE, DigitalObjectState.ACTIVE, VERSION_2);
                repository.save(activeObj);
                entityManager.flush();

                // When
                Optional<DigitalObject> result = repository.findRelated(TEST_PID, CURRENT_USER.getId());

                // Then
                assertThat(result).isPresent();
                assertThat(result.get().getState()).isEqualTo(DigitalObjectState.ACTIVE);
        }

    @Test
    @DisplayName("Find by PID and instance ID should return correct object")
    void findByPidAndInstanceId_shouldReturnCorrectObject() {
        // Given
        repository.save(createDigitalObject(TEST_PID, CURRENT_USER, "inst1",
                DigitalObjectState.ACTIVE, VERSION_1));
        repository.save(createDigitalObject(TEST_PID, CURRENT_USER, "inst2",
                DigitalObjectState.ACTIVE, VERSION_2));
        entityManager.flush();

        // When
        Optional<DigitalObject> result = repository.findByPidAndInstanceId(TEST_PID, "inst2");

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getInstanceId()).isEqualTo("inst2");
    }
}