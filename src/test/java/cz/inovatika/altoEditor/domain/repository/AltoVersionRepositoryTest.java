package cz.inovatika.altoEditor.domain.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.TestPropertySource;

import cz.inovatika.altoEditor.domain.enums.AltoVersionState;
import cz.inovatika.altoEditor.domain.model.AltoVersion;
import cz.inovatika.altoEditor.domain.model.DigitalObject;
import cz.inovatika.altoEditor.domain.model.User;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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

    private User ALTO_EDITOR_USER;
    private User PERO_USER;
    private User CURRENT_USER;

    private static final UUID TEST_UUID = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
    private static final String TEST_PID = "uuid:" + TEST_UUID;
    private static final UUID TEST_UUID_2 = UUID.fromString("223e4567-e89b-12d3-a456-426614174000");
    private static final String TEST_PID_2 = "uuid:" + TEST_UUID_2;
    private static final String TEST_INSTANCE = "dk";
    private static final int VERSION_1 = 1;
    private static final int VERSION_2 = 2;
    private static final String CONTENT_HASH = "a1b2c3d4e5f6";

    private AltoVersion createAltoVersion(String pid, User user, String instanceId,
            AltoVersionState state, int version) {
        return createAltoVersion(pid, user, instanceId, state, version, CONTENT_HASH);
    }

    private AltoVersion createAltoVersion(String pid, User user, String instanceId,
            AltoVersionState state, int version, String contentHash) {
        DigitalObject dobj = digitalObjectRepository.save(DigitalObject.builder().pid(pid).build());
        Set<String> instances = new HashSet<>(instanceId != null ? Set.of(instanceId) : Set.of());
        return AltoVersion.builder()
                .digitalObject(dobj)
                .user(user)
                .presentInInstances(instances)
                .state(state)
                .version(version)
                .contentHash(contentHash != null ? contentHash : CONTENT_HASH)
                .build();
    }

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();

        ALTO_EDITOR_USER = userRepository.save(User.builder().username("kramerius").build());
        PERO_USER = userRepository.save(User.builder().username("pero").engine(true).build());
        CURRENT_USER = userRepository.save(User.builder().username("current_user").build());

        repository.deleteAll();
    }

    @Nested
    @DisplayName("save and basic queries")
    class SaveAndBasicQueries {

        @Test
        @DisplayName("when saving AltoVersion then id and createdAt are set")
        void whenSaveAltoVersion_thenDateIsSet() {
            AltoVersion obj = createAltoVersion(TEST_PID, CURRENT_USER, TEST_INSTANCE,
                    AltoVersionState.ACTIVE, VERSION_1);

            AltoVersion saved = repository.save(obj);
            entityManager.flush();

            assertThat(saved.getId()).isNotNull();
            assertThat(saved.getCreatedAt()).isNotNull();
        }

        @Test
        @DisplayName("findAllByDigitalObjectUuid returns all versions for that UUID")
        void findAllByDigitalObjectUuid_returnsAllForUuid() {
            repository.save(createAltoVersion(TEST_PID, CURRENT_USER, TEST_INSTANCE, AltoVersionState.ACTIVE, VERSION_1));
            repository.save(createAltoVersion(TEST_PID, PERO_USER, TEST_INSTANCE, AltoVersionState.ACTIVE, VERSION_2));
            repository.save(createAltoVersion(TEST_PID_2, CURRENT_USER, TEST_INSTANCE, AltoVersionState.ACTIVE, VERSION_1));
            entityManager.flush();

            List<AltoVersion> result = repository.findAllByDigitalObjectUuid(TEST_UUID);

            assertThat(result).hasSize(2);
            assertThat(result).allMatch(av -> av.getDigitalObject().getUuid().equals(TEST_UUID));
        }

        @Test
        @DisplayName("existsByDigitalObjectUuid returns true when any version exists")
        void existsByDigitalObjectUuid_returnsTrue_whenExists() {
            repository.save(createAltoVersion(TEST_PID, CURRENT_USER, TEST_INSTANCE, AltoVersionState.ACTIVE, VERSION_1));
            entityManager.flush();

            boolean exists = repository.existsByDigitalObjectUuid(TEST_UUID);

            assertThat(exists).isTrue();
        }

        @Test
        @DisplayName("existsByDigitalObjectUuid returns false when no version exists")
        void existsByDigitalObjectUuid_returnsFalse_whenNotExists() {
            boolean exists = repository.existsByDigitalObjectUuid(UUID.fromString("123e4567-e89b-12d3-a456-426614174999"));

            assertThat(exists).isFalse();
        }

        @Test
        @DisplayName("findByDigitalObjectUuidAndVersion returns the matching version")
        void findByDigitalObjectUuidAndVersion_returnsMatchingVersion() {
            repository.save(createAltoVersion(TEST_PID, CURRENT_USER, TEST_INSTANCE, AltoVersionState.ACTIVE, VERSION_1));
            repository.save(createAltoVersion(TEST_PID, CURRENT_USER, TEST_INSTANCE, AltoVersionState.ACTIVE, VERSION_2));
            entityManager.flush();

            Optional<AltoVersion> result = repository.findByDigitalObjectUuidAndVersion(TEST_UUID, VERSION_2);

            assertThat(result).isPresent();
            assertThat(result.get().getVersion()).isEqualTo(VERSION_2);
        }

        @Test
        @DisplayName("findFirstByDigitalObjectUuidOrderByVersionDesc returns highest version")
        void findFirstByDigitalObjectUuidOrderByVersionDesc_returnsMaxVersion() {
            repository.save(createAltoVersion(TEST_PID, ALTO_EDITOR_USER, TEST_INSTANCE, AltoVersionState.ACTIVE, 3));
            repository.save(createAltoVersion(TEST_PID, CURRENT_USER, TEST_INSTANCE, AltoVersionState.ARCHIVED, 1));
            repository.save(createAltoVersion(TEST_PID, PERO_USER, TEST_INSTANCE, AltoVersionState.ARCHIVED, 2));
            entityManager.flush();

            Optional<AltoVersion> result = repository.findFirstByDigitalObjectUuidOrderByVersionDesc(TEST_UUID);

            assertThat(result).isPresent();
            assertThat(result.get().getVersion()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("findActive")
    class FindActive {

        @Test
        @DisplayName("returns ACTIVE version when present")
        void returnsActiveVersion_whenPresent() {
            repository.save(createAltoVersion(TEST_PID, CURRENT_USER, TEST_INSTANCE, AltoVersionState.ACTIVE, VERSION_1));
            entityManager.flush();

            Optional<AltoVersion> result = repository.findActive(TEST_UUID);

            assertThat(result).isPresent();
            assertThat(result.get().getState()).isEqualTo(AltoVersionState.ACTIVE);
            assertThat(result.get().getVersion()).isEqualTo(VERSION_1);
        }

        @Test
        @DisplayName("returns empty when only PENDING versions exist")
        void returnsEmpty_whenOnlyPending() {
            repository.save(createAltoVersion(TEST_PID, CURRENT_USER, TEST_INSTANCE, AltoVersionState.PENDING, VERSION_1));
            entityManager.flush();

            Optional<AltoVersion> result = repository.findActive(TEST_UUID);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns empty when no version exists for UUID")
        void returnsEmpty_whenNoVersion() {
            Optional<AltoVersion> result = repository.findActive(TEST_UUID);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("findPendingForUser")
    class FindPendingForUser {

        @Test
        @DisplayName("returns PENDING version for the given user")
        void returnsPendingVersion_forUser() {
            repository.save(createAltoVersion(TEST_PID, CURRENT_USER, TEST_INSTANCE, AltoVersionState.PENDING, VERSION_1));
            entityManager.flush();

            Optional<AltoVersion> result = repository.findPendingForUser(TEST_UUID, CURRENT_USER.getId());

            assertThat(result).isPresent();
            assertThat(result.get().getUser().getId()).isEqualTo(CURRENT_USER.getId());
            assertThat(result.get().getState()).isEqualTo(AltoVersionState.PENDING);
        }

        @Test
        @DisplayName("returns empty when user has no PENDING version")
        void returnsEmpty_whenUserHasNoPending() {
            repository.save(createAltoVersion(TEST_PID, PERO_USER, TEST_INSTANCE, AltoVersionState.PENDING, VERSION_1));
            entityManager.flush();

            Optional<AltoVersion> result = repository.findPendingForUser(TEST_UUID, CURRENT_USER.getId());

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns empty when user's version is not PENDING")
        void returnsEmpty_whenUserVersionNotPending() {
            repository.save(createAltoVersion(TEST_PID, CURRENT_USER, TEST_INSTANCE, AltoVersionState.ACTIVE, VERSION_1));
            entityManager.flush();

            Optional<AltoVersion> result = repository.findPendingForUser(TEST_UUID, CURRENT_USER.getId());

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("findStaleForUser")
    class FindStaleForUser {

        @Test
        @DisplayName("returns STALE version for the given user")
        void returnsStaleVersion_forUser() {
            repository.save(createAltoVersion(TEST_PID, CURRENT_USER, TEST_INSTANCE, AltoVersionState.STALE, VERSION_1));
            entityManager.flush();

            Optional<AltoVersion> result = repository.findStaleForUser(TEST_UUID, CURRENT_USER.getId());

            assertThat(result).isPresent();
            assertThat(result.get().getUser().getId()).isEqualTo(CURRENT_USER.getId());
            assertThat(result.get().getState()).isEqualTo(AltoVersionState.STALE);
        }

        @Test
        @DisplayName("returns empty when user has no STALE version")
        void returnsEmpty_whenUserHasNoStale() {
            repository.save(createAltoVersion(TEST_PID, CURRENT_USER, TEST_INSTANCE, AltoVersionState.ACTIVE, VERSION_1));
            entityManager.flush();

            Optional<AltoVersion> result = repository.findStaleForUser(TEST_UUID, CURRENT_USER.getId());

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("findEngineUpdateCandidate")
    class FindEngineUpdateCandidate {

        @Test
        @DisplayName("returns version when user, contentHash and state match")
        void returnsVersion_whenUserHashAndStateMatch() {
            repository.save(createAltoVersion(TEST_PID, CURRENT_USER, TEST_INSTANCE, AltoVersionState.ACTIVE, VERSION_1, "abc123"));
            entityManager.flush();

            Optional<AltoVersion> result = repository.findEngineUpdateCandidate(TEST_UUID, CURRENT_USER.getId(), "abc123");

            assertThat(result).isPresent();
            assertThat(result.get().getContentHash()).isEqualTo("abc123");
            assertThat(result.get().getState()).isEqualTo(AltoVersionState.ACTIVE);
        }

        @Test
        @DisplayName("returns empty when contentHash does not match")
        void returnsEmpty_whenContentHashMismatch() {
            repository.save(createAltoVersion(TEST_PID, CURRENT_USER, TEST_INSTANCE, AltoVersionState.ACTIVE, VERSION_1, "hash1"));
            entityManager.flush();

            Optional<AltoVersion> result = repository.findEngineUpdateCandidate(TEST_UUID, CURRENT_USER.getId(), "hash2");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns version in PENDING state")
        void returnsVersion_inPendingState() {
            repository.save(createAltoVersion(TEST_PID, CURRENT_USER, TEST_INSTANCE, AltoVersionState.PENDING, VERSION_1, "same"));
            entityManager.flush();

            Optional<AltoVersion> result = repository.findEngineUpdateCandidate(TEST_UUID, CURRENT_USER.getId(), "same");

            assertThat(result).isPresent();
            assertThat(result.get().getState()).isEqualTo(AltoVersionState.PENDING);
        }
    }

    @Nested
    @DisplayName("findRelated")
    class FindRelated {

        @Test
        @DisplayName("prioritizes current user's version over ACTIVE")
        void prioritizesUserOverActive() {
            AltoVersion userObj = createAltoVersion(TEST_PID, CURRENT_USER, TEST_INSTANCE, AltoVersionState.ARCHIVED, VERSION_1);
            AltoVersion activeObj = createAltoVersion(TEST_PID, PERO_USER, TEST_INSTANCE, AltoVersionState.ACTIVE, VERSION_2);
            repository.save(userObj);
            repository.save(activeObj);
            entityManager.flush();

            Optional<AltoVersion> result = repository.findRelated(TEST_UUID, CURRENT_USER.getId());

            assertThat(result).isPresent();
            assertThat(result.get().getUser().getId()).isEqualTo(CURRENT_USER.getId());
            assertThat(result.get().getState()).isEqualTo(AltoVersionState.ARCHIVED);
        }

        @Test
        @DisplayName("returns ACTIVE version when user has no version")
        void returnsActive_whenUserHasNone() {
            repository.save(createAltoVersion(TEST_PID, PERO_USER, TEST_INSTANCE, AltoVersionState.ACTIVE, VERSION_2));
            entityManager.flush();

            Optional<AltoVersion> result = repository.findRelated(TEST_UUID, CURRENT_USER.getId());

            assertThat(result).isPresent();
            assertThat(result.get().getState()).isEqualTo(AltoVersionState.ACTIVE);
        }

        @Test
        @DisplayName("returns empty when no user or ACTIVE version exists")
        void returnsEmpty_whenNoMatch() {
            repository.save(createAltoVersion(TEST_PID, PERO_USER, TEST_INSTANCE, AltoVersionState.PENDING, VERSION_2));
            entityManager.flush();

            Optional<AltoVersion> result = repository.findRelated(TEST_UUID, CURRENT_USER.getId());

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("removeInstanceAssociation")
    class RemoveInstanceAssociation {

        @Test
        @DisplayName("removes instance from presentInInstances for versions with given UUID")
        void removesInstanceFromPresentInInstances() {
            AltoVersion av = createAltoVersion(TEST_PID, CURRENT_USER, TEST_INSTANCE, AltoVersionState.ACTIVE, VERSION_1);
            av.getPresentInInstances().add("inst1");
            av.getPresentInInstances().add("inst2");
            repository.save(av);
            entityManager.flush();
            entityManager.clear();

            repository.removeInstanceAssociation(TEST_UUID, "inst1");
            entityManager.flush();
            entityManager.clear();

            Optional<AltoVersion> reloaded = repository.findByDigitalObjectUuidAndVersion(TEST_UUID, VERSION_1);
            assertThat(reloaded).isPresent();
            assertThat(reloaded.get().getPresentInInstances())
                    .doesNotContain("inst1")
                    .contains("inst2");
        }
    }
}
