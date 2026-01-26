package cz.inovatika.altoEditor.domain.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.TestPropertySource;

import cz.inovatika.altoEditor.domain.enums.DigitalObjectState;
import cz.inovatika.altoEditor.domain.model.DigitalObject;

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

    // Test user IDs
    private static final Integer CURRENT_USER_ID = 1;
    private static final Integer PERO_USER_ID = 2;
    private static final Integer ALTO_EDITOR_USER_ID = 3;
    private static final Integer OTHER_USER_ID = 4;

    // Test constants
    private static final String TEST_PID = "uuid:12345";
    private static final String TEST_INSTANCE = "dk";
    private static final int VERSION_1 = 1;
    private static final int VERSION_2 = 2;

    private DigitalObject createDigitalObject(String pid, Integer userId, String instanceId, 
                                             DigitalObjectState state, int version) {
        return DigitalObject.builder()
                .pid(pid)
                .userId(userId)
                .instanceId(instanceId)
                .state(state)
                .version(version)
                .label("Test Label")
                .parentPath("/test/path")
                .parentLabel("Parent Label")
                .lock(false)
                .build();
    }

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @Test
    @DisplayName("When saving digital object, then date is automatically set")
    void whenSaveDigitalObject_thenDateIsSet() {
        // Given
        DigitalObject obj = createDigitalObject(TEST_PID, CURRENT_USER_ID, TEST_INSTANCE, 
                                               DigitalObjectState.NEW, VERSION_1);

        // When
        DigitalObject saved = repository.save(obj);
        entityManager.flush();

        // Then
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getDate()).isNotNull();
        assertThat(saved.getDate()).isBeforeOrEqualTo(LocalDateTime.now());
    }

    @Test
    @DisplayName("When updating digital object, then date is updated")
    void whenUpdateDigitalObject_thenDateIsUpdated() throws InterruptedException {
        // Given
        DigitalObject obj = createDigitalObject(TEST_PID, CURRENT_USER_ID, TEST_INSTANCE, 
                                               DigitalObjectState.NEW, VERSION_1);
        DigitalObject saved = repository.save(obj);
        entityManager.flush();
        LocalDateTime originalDate = saved.getDate();

        Thread.sleep(10);

        // When
        saved.setState(DigitalObjectState.EDITED);
        repository.save(saved);
        entityManager.flush();

        // Then
        DigitalObject updated = repository.findById(saved.getId()).orElseThrow();
        assertThat(updated.getDate()).isAfter(originalDate);
    }

    @Test
    @DisplayName("Find by PID should return all objects with that PID")
    void findByPid_shouldReturnAllObjects() {
        // Given
        repository.save(createDigitalObject(TEST_PID, CURRENT_USER_ID, TEST_INSTANCE, 
                                           DigitalObjectState.NEW, VERSION_1));
        repository.save(createDigitalObject(TEST_PID, PERO_USER_ID, TEST_INSTANCE, 
                                           DigitalObjectState.NEW, VERSION_2));
        repository.save(createDigitalObject("uuid:other", CURRENT_USER_ID, TEST_INSTANCE, 
                                           DigitalObjectState.NEW, VERSION_1));
        entityManager.flush();

        // When
        List<DigitalObject> result = repository.findByPid(TEST_PID);

        // Then
        assertThat(result).hasSize(2);
        assertThat(result).allMatch(obj -> obj.getPid().equals(TEST_PID));
    }

    @Test
    @DisplayName("Exists by PID should return true when object exists")
    void existsByPid_shouldReturnTrue_whenExists() {
        // Given
        repository.save(createDigitalObject(TEST_PID, CURRENT_USER_ID, TEST_INSTANCE, 
                                           DigitalObjectState.NEW, VERSION_1));
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
        repository.save(createDigitalObject(TEST_PID, CURRENT_USER_ID, "dk", 
                                           DigitalObjectState.NEW, VERSION_1));
        repository.save(createDigitalObject(TEST_PID, CURRENT_USER_ID, "mzk", 
                                           DigitalObjectState.NEW, VERSION_1));
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
        repository.save(createDigitalObject(TEST_PID, CURRENT_USER_ID, TEST_INSTANCE, 
                                           DigitalObjectState.NEW, VERSION_1));
        repository.save(createDigitalObject(TEST_PID, PERO_USER_ID, TEST_INSTANCE, 
                                           DigitalObjectState.NEW, VERSION_1));
        entityManager.flush();

        // When
        Optional<DigitalObject> result = repository.findByPidAndUserId(TEST_PID, CURRENT_USER_ID);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getUserId()).isEqualTo(CURRENT_USER_ID);
    }

    @Test
    @DisplayName("Find all by PID and user ID should return all matching objects")
    void findAllByPidAndUserId_shouldReturnAllMatching() {
        // Given
        repository.save(createDigitalObject(TEST_PID, CURRENT_USER_ID, TEST_INSTANCE, 
                                           DigitalObjectState.NEW, VERSION_1));
        repository.save(createDigitalObject(TEST_PID, CURRENT_USER_ID, TEST_INSTANCE, 
                                           DigitalObjectState.EDITED, VERSION_2));
        repository.save(createDigitalObject(TEST_PID, PERO_USER_ID, TEST_INSTANCE, 
                                           DigitalObjectState.NEW, VERSION_1));
        entityManager.flush();

        // When
        List<DigitalObject> result = repository.findAllByPidAndUserId(TEST_PID, CURRENT_USER_ID);

        // Then
        assertThat(result).hasSize(2);
        assertThat(result).allMatch(obj -> obj.getUserId().equals(CURRENT_USER_ID));
    }

    @Test
    @DisplayName("Find by PID, user ID and version should return specific version")
    void findByPidAndUserIdAndVersion_shouldReturnSpecificVersion() {
        // Given
        repository.save(createDigitalObject(TEST_PID, CURRENT_USER_ID, TEST_INSTANCE, 
                                           DigitalObjectState.NEW, VERSION_1));
        repository.save(createDigitalObject(TEST_PID, CURRENT_USER_ID, TEST_INSTANCE, 
                                           DigitalObjectState.EDITED, VERSION_2));
        entityManager.flush();

        // When
        Optional<DigitalObject> result = repository.findByPidAndUserIdAndVersion(
            TEST_PID, CURRENT_USER_ID, VERSION_2
        );

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getVersion()).isEqualTo(VERSION_2);
    }

    @Test
    @DisplayName("Find by PID with priority should order by version match first")
    void findByPidWithPriority_shouldPrioritizeVersionMatch() {
        // Given
        DigitalObject currentUserV1 = createDigitalObject(TEST_PID, CURRENT_USER_ID, TEST_INSTANCE, 
                                                         DigitalObjectState.NEW, VERSION_1);
        DigitalObject peroUserV2 = createDigitalObject(TEST_PID, PERO_USER_ID, TEST_INSTANCE, 
                                                       DigitalObjectState.NEW, VERSION_2);
        DigitalObject altoUserV1 = createDigitalObject(TEST_PID, ALTO_EDITOR_USER_ID, TEST_INSTANCE, 
                                                       DigitalObjectState.NEW, VERSION_1);
        
        repository.saveAll(List.of(currentUserV1, peroUserV2, altoUserV1));
        entityManager.flush();

        // When - looking for VERSION_2
        Optional<DigitalObject> result = repository.findByPidAndVersionAndUsersWithPriority(
            TEST_PID, VERSION_2, CURRENT_USER_ID, PERO_USER_ID, ALTO_EDITOR_USER_ID
        );

        // Then - PERO user's V2 should be first (version match)
        assertThat(result).isPresent();
        assertThat(result.get().getUserId()).isEqualTo(PERO_USER_ID);
        assertThat(result.get().getVersion()).isEqualTo(VERSION_2);
    }

    @Test
    @DisplayName("Find by PID with priority should prioritize current user when version is null")
    void findByPidWithPriority_shouldPrioritizeCurrentUser_whenVersionNull() {
        // Given
        DigitalObject currentUser = createDigitalObject(TEST_PID, CURRENT_USER_ID, TEST_INSTANCE, 
                                                       DigitalObjectState.NEW, VERSION_1);
        DigitalObject peroUser = createDigitalObject(TEST_PID, PERO_USER_ID, TEST_INSTANCE, 
                                                     DigitalObjectState.NEW, VERSION_1);
        DigitalObject altoUser = createDigitalObject(TEST_PID, ALTO_EDITOR_USER_ID, TEST_INSTANCE, 
                                                     DigitalObjectState.NEW, VERSION_1);
        
        repository.saveAll(List.of(peroUser, altoUser, currentUser)); // Save in mixed order
        entityManager.flush();

        // When - version is null
        Optional<DigitalObject> result = repository.findByPidAndVersionAndUsersWithPriority(
            TEST_PID, null, CURRENT_USER_ID, PERO_USER_ID, ALTO_EDITOR_USER_ID
        );

        // Then - Current user first, then PERO, then ALTO
        assertThat(result).isPresent();
        assertThat(result.get().getUserId()).isEqualTo(CURRENT_USER_ID);
    }

    @Test
    @DisplayName("Find by PID with priority should prioritize PERO user second")
    void findByPidWithPriority_shouldPrioritizePeroUserSecond() {
        // Given
        DigitalObject currentUser = createDigitalObject(TEST_PID, CURRENT_USER_ID, TEST_INSTANCE, 
                                                       DigitalObjectState.NEW, VERSION_1);
        DigitalObject peroUser = createDigitalObject(TEST_PID, PERO_USER_ID, TEST_INSTANCE, 
                                                     DigitalObjectState.NEW, VERSION_1);
        DigitalObject altoUser = createDigitalObject(TEST_PID, ALTO_EDITOR_USER_ID, TEST_INSTANCE, 
                                                     DigitalObjectState.NEW, VERSION_1);
        
        repository.saveAll(List.of(peroUser, altoUser, currentUser)); // Save in mixed order
        entityManager.flush();

        // When - version is null
        Optional<DigitalObject> result = repository.findByPidAndVersionAndUsersWithPriority(
            TEST_PID, null, OTHER_USER_ID, PERO_USER_ID, ALTO_EDITOR_USER_ID
        );

        // Then - Current PERO user first
        assertThat(result).isPresent();
        assertThat(result.get().getUserId()).isEqualTo(PERO_USER_ID);
    }

    @Test
    @DisplayName("Find by PID with priority should prioritize ALTO user last")
    void findByPidWithPriority_shouldPrioritizeAltoUserLast() {
        // Given
        DigitalObject currentUser = createDigitalObject(TEST_PID, CURRENT_USER_ID, TEST_INSTANCE, 
                                                       DigitalObjectState.NEW, VERSION_1);
        DigitalObject peroUser = createDigitalObject(TEST_PID, PERO_USER_ID, TEST_INSTANCE, 
                                                     DigitalObjectState.NEW, VERSION_1);
        DigitalObject altoUser = createDigitalObject(TEST_PID, ALTO_EDITOR_USER_ID, TEST_INSTANCE, 
                                                     DigitalObjectState.NEW, VERSION_1);
        
        repository.saveAll(List.of(peroUser, altoUser, currentUser)); // Save in mixed order
        entityManager.flush();

        // When - version is null
        Optional<DigitalObject> result = repository.findByPidAndVersionAndUsersWithPriority(
            TEST_PID, null, OTHER_USER_ID, OTHER_USER_ID, ALTO_EDITOR_USER_ID
        );

        // Then - Current ALTO_EDITOR user first
        assertThat(result).isPresent();
        assertThat(result.get().getUserId()).isEqualTo(ALTO_EDITOR_USER_ID);
    }

    @Test
    @DisplayName("Find update candidate should prioritize user's NEW or EDITED objects")
    void findUpdateCandidate_shouldPrioritizeUsersNewOrEdited() {
        // Given
        DigitalObject userNew = createDigitalObject(TEST_PID, CURRENT_USER_ID, TEST_INSTANCE, 
                                                   DigitalObjectState.NEW, VERSION_1);
        DigitalObject uploaded = createDigitalObject(TEST_PID, OTHER_USER_ID, TEST_INSTANCE, 
                                                     DigitalObjectState.UPLOADED, VERSION_2);
        DigitalObject altoNew = createDigitalObject(TEST_PID, ALTO_EDITOR_USER_ID, TEST_INSTANCE, 
                                                    DigitalObjectState.NEW, VERSION_1);
        
        repository.saveAll(List.of(uploaded, altoNew, userNew));
        entityManager.flush();

        // When
        Optional<DigitalObject> result = repository.findUpdateCandidate(
            TEST_PID, CURRENT_USER_ID, ALTO_EDITOR_USER_ID
        );

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getUserId()).isEqualTo(CURRENT_USER_ID);
        assertThat(result.get().getState()).isEqualTo(DigitalObjectState.NEW);
    }

    @Test
    @DisplayName("Find update candidate should return UPLOADED when user has no NEW/EDITED")
    void findUpdateCandidate_shouldReturnUploaded_whenUserHasNoNewOrEdited() {
        // Given
        DigitalObject uploaded = createDigitalObject(TEST_PID, OTHER_USER_ID, TEST_INSTANCE, 
                                                     DigitalObjectState.UPLOADED, VERSION_1);
        DigitalObject userAccepted = createDigitalObject(TEST_PID, CURRENT_USER_ID, TEST_INSTANCE, 
                                                        DigitalObjectState.ACCEPTED, VERSION_1);
        
        repository.saveAll(List.of(uploaded, userAccepted));
        entityManager.flush();

        // When
        Optional<DigitalObject> result = repository.findUpdateCandidate(
            TEST_PID, CURRENT_USER_ID, ALTO_EDITOR_USER_ID
        );

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getState()).isEqualTo(DigitalObjectState.UPLOADED);
    }

    @Test
    @DisplayName("Find update candidate should return most recent when multiple candidates")
    void findUpdateCandidate_shouldReturnMostRecent() throws InterruptedException {
        // Given
        DigitalObject older = createDigitalObject(TEST_PID, CURRENT_USER_ID, TEST_INSTANCE, 
                                                 DigitalObjectState.NEW, VERSION_1);
        repository.save(older);
        entityManager.flush();
        
        Thread.sleep(10);
        
        DigitalObject newer = createDigitalObject(TEST_PID, CURRENT_USER_ID, TEST_INSTANCE, 
                                                 DigitalObjectState.NEW, VERSION_2);
        repository.save(newer);
        entityManager.flush();

        // When
        Optional<DigitalObject> result = repository.findUpdateCandidate(
            TEST_PID, CURRENT_USER_ID, ALTO_EDITOR_USER_ID
        );

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getVersion()).isEqualTo(VERSION_2);
    }

    @Test
    @DisplayName("Find first by PID ordered by version desc should return max version object")
    void findFirstByPidOrderByVersionDesc_shouldReturnMaxVersionObjects() {
        // Given
        repository.save(createDigitalObject(TEST_PID, ALTO_EDITOR_USER_ID, TEST_INSTANCE, 
                                           DigitalObjectState.NEW, 3));
        repository.save(createDigitalObject(TEST_PID, CURRENT_USER_ID, TEST_INSTANCE, 
                                           DigitalObjectState.NEW, 1));
        repository.save(createDigitalObject(TEST_PID, PERO_USER_ID, TEST_INSTANCE, 
                                           DigitalObjectState.NEW, 2));
        entityManager.flush();

        // When
        Optional<DigitalObject> result = repository.findFirstByPidOrderByVersionDesc(TEST_PID);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getVersion()).isEqualTo(3);
    }
}