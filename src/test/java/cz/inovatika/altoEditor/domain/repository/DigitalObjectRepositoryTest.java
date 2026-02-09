package cz.inovatika.altoEditor.domain.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.TestPropertySource;

import cz.inovatika.altoEditor.domain.enums.AltoVersionState;
import cz.inovatika.altoEditor.domain.enums.Model;
import cz.inovatika.altoEditor.domain.model.DigitalObject;
import cz.inovatika.altoEditor.domain.model.dto.PageCountStats;
import cz.inovatika.altoEditor.domain.model.AltoVersion;
import cz.inovatika.altoEditor.domain.model.User;

@DataJpaTest
@TestPropertySource(properties = "altoeditor.home=src/test/resources")
class DigitalObjectRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private DigitalObjectRepository repository;

    @Autowired
    private AltoVersionRepository altoVersionRepository;

    @Autowired
    private UserRepository userRepository;

    private static final UUID ROOT_UUID = UUID.fromString("11111111-e89b-12d3-a456-426614174000");
    private static final String ROOT_PID = "uuid:" + ROOT_UUID;
    private static final UUID PAGE1_UUID = UUID.fromString("22222222-e89b-12d3-a456-426614174000");
    private static final String PAGE1_PID = "uuid:" + PAGE1_UUID;
    private static final UUID PAGE2_UUID = UUID.fromString("33333333-e89b-12d3-a456-426614174000");
    private static final String PAGE2_PID = "uuid:" + PAGE2_UUID;

    private User testUser;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        testUser = userRepository.save(User.builder().username("test-user").build());

        altoVersionRepository.deleteAll();
        repository.deleteAll();
    }

    @Nested
    @DisplayName("save and findById")
    class SaveAndFindById {

        @Test
        @DisplayName("saves DigitalObject with pid and finds by uuid")
        void savesAndFindsById() {
            DigitalObject root = DigitalObject.builder()
                    .pid(ROOT_PID)
                    .model(Model.PAGE.getModelName())
                    .title("Root")
                    .build();

            repository.save(root);
            entityManager.flush();
            entityManager.clear();

            Optional<DigitalObject> found = repository.findById(ROOT_UUID);

            assertThat(found).isPresent();
            assertThat(found.get().getUuid()).isEqualTo(ROOT_UUID);
            assertThat(found.get().getPid()).isEqualTo(ROOT_PID);
            assertThat(found.get().getTitle()).isEqualTo("Root");
        }

        @Test
        @DisplayName("saves hierarchy with parent and finds by id")
        void savesHierarchyAndFindsById() {
            DigitalObject root = repository.save(DigitalObject.builder()
                    .pid(ROOT_PID)
                    .model("monograph")
                    .title("Monograph")
                    .build());
            repository.save(DigitalObject.builder()
                    .pid(PAGE1_PID)
                    .model(Model.PAGE.getModelName())
                    .title("Page 1")
                    .parent(root)
                    .build());
            entityManager.flush();
            entityManager.clear();

            Optional<DigitalObject> foundRoot = repository.findById(ROOT_UUID);
            Optional<DigitalObject> foundPage = repository.findById(PAGE1_UUID);

            assertThat(foundRoot).isPresent();
            assertThat(foundPage).isPresent();
            assertThat(foundPage.get().getParent().getUuid()).isEqualTo(ROOT_UUID);
        }

        @Test
        @DisplayName("returns empty when uuid does not exist")
        void returnsEmpty_whenUuidNotFound() {
            Optional<DigitalObject> found = repository.findById(UUID.fromString("99999999-e89b-12d3-a456-426614174000"));

            assertThat(found).isEmpty();
        }
    }

    @Nested
    @DisplayName("getDescendantPageStats")
    class GetDescendantPageStats {

        @Test
        @DisplayName("returns zero counts when root has no descendants")
        void returnsZeroCounts_whenNoDescendants() {
            repository.save(DigitalObject.builder()
                    .pid(ROOT_PID)
                    .model("monograph")
                    .title("Root")
                    .build());
            entityManager.flush();

            PageCountStats stats = repository.getDescendantPageStats(ROOT_UUID);

            assertThat(stats.getTotalPages()).isEqualTo(0);
            assertThat(stats.getPagesWithAlto()).isEqualTo(0);
        }

        @Test
        @DisplayName("returns total descendant pages excluding root")
        void returnsTotalDescendantPages() {
            DigitalObject root = repository.save(DigitalObject.builder()
                    .pid(ROOT_PID)
                    .model("monograph")
                    .title("Root")
                    .build());
            repository.save(DigitalObject.builder()
                    .pid(PAGE1_PID)
                    .model(Model.PAGE.getModelName())
                    .title("Page 1")
                    .parent(root)
                    .build());
            repository.save(DigitalObject.builder()
                    .pid(PAGE2_PID)
                    .model(Model.PAGE.getModelName())
                    .title("Page 2")
                    .parent(root)
                    .build());
            entityManager.flush();

            PageCountStats stats = repository.getDescendantPageStats(ROOT_UUID);

            assertThat(stats.getTotalPages()).isEqualTo(2);
            assertThat(stats.getPagesWithAlto()).isEqualTo(0);
        }

        @Test
        @DisplayName("returns pagesWithAlto for descendants that have ALTO versions")
        void returnsPagesWithAlto_whenAltoVersionsExist() {
            DigitalObject root = repository.save(DigitalObject.builder()
                    .pid(ROOT_PID)
                    .model("monograph")
                    .title("Root")
                    .build());
            DigitalObject page1 = repository.save(DigitalObject.builder()
                    .pid(PAGE1_PID)
                    .model(Model.PAGE.getModelName())
                    .title("Page 1")
                    .parent(root)
                    .build());
            repository.save(DigitalObject.builder()
                    .pid(PAGE2_PID)
                    .model(Model.PAGE.getModelName())
                    .title("Page 2")
                    .parent(root)
                    .build());
            entityManager.flush();

            altoVersionRepository.save(AltoVersion.builder()
                    .digitalObject(page1)
                    .user(testUser)
                    .version(0)
                    .state(AltoVersionState.ACTIVE)
                    .contentHash("abc123")
                    .build());
            entityManager.flush();

            PageCountStats stats = repository.getDescendantPageStats(ROOT_UUID);

            assertThat(stats.getTotalPages()).isEqualTo(2);
            assertThat(stats.getPagesWithAlto()).isEqualTo(1);
        }

        @Test
        @DisplayName("counts only page descendants (not other models)")
        void countsOnlyPageDescendants() {
            DigitalObject root = repository.save(DigitalObject.builder()
                    .pid(ROOT_PID)
                    .model("monograph")
                    .title("Root")
                    .build());
            UUID periodicalUuid = UUID.fromString("44444444-e89b-12d3-a456-426614174000");
            repository.save(DigitalObject.builder()
                    .pid("uuid:" + periodicalUuid)
                    .model("periodical")
                    .title("Periodical")
                    .parent(root)
                    .build());
            repository.save(DigitalObject.builder()
                    .pid(PAGE1_PID)
                    .model(Model.PAGE.getModelName())
                    .title("Page 1")
                    .parent(root)
                    .build());
            entityManager.flush();

            PageCountStats stats = repository.getDescendantPageStats(ROOT_UUID);

            assertThat(stats.getTotalPages()).isEqualTo(1);
        }
    }
}
