package cz.inovatika.altoEditor.domain.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.TestPropertySource;

import cz.inovatika.altoEditor.domain.enums.BatchPriority;
import cz.inovatika.altoEditor.domain.enums.BatchState;
import cz.inovatika.altoEditor.domain.enums.BatchType;
import cz.inovatika.altoEditor.domain.model.Batch;
import cz.inovatika.altoEditor.domain.model.User;

@DataJpaTest
@TestPropertySource(properties = "altoeditor.home=src/test/resources")
class BatchRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private BatchRepository batchRepository;

    @Autowired
    private UserRepository userRepository;

    private User testUser;

    private Batch createBatch(String pid, BatchState state, String instance) {
        return Batch.builder()
                .type(BatchType.GENERATE_SINGLE)
                .priority(BatchPriority.MEDIUM)
                .pid(pid)
                .instance(instance)
                .state(state)
                .createdBy(testUser)
                .build();
    }

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        testUser = userRepository.save(User.builder().username("batch-user").build());

        batchRepository.deleteAll();
    }

    @Nested
    @DisplayName("save and auditing")
    class SaveAndAuditing {

        @Test
        @DisplayName("when saving batch then id, createdAt and updatedAt are set")
        void whenSaveBatch_thenCreatedAtAndUpdatedAtAreSet() {
            Batch batch = createBatch("uuid:12345", BatchState.PLANNED, "dk");

            Batch saved = batchRepository.save(batch);
            entityManager.flush();

            assertThat(saved.getId()).isNotNull();
            assertThat(saved.getCreatedAt()).isNotNull();
            assertThat(saved.getUpdatedAt()).isNotNull();
            assertThat(saved.getCreatedAt()).isEqualTo(saved.getUpdatedAt());
        }

        @Test
        @DisplayName("when updating batch then updatedAt changes")
        void whenUpdateBatch_thenUpdatedAtChanges() throws InterruptedException {
            Batch batch = createBatch("uuid:12345", BatchState.PLANNED, "dk");
            Batch saved = batchRepository.save(batch);
            entityManager.flush();
            LocalDateTime originalUpdatedAt = saved.getUpdatedAt();

            Thread.sleep(10);

            saved.setState(BatchState.RUNNING);
            batchRepository.save(saved);
            entityManager.flush();

            Batch updated = batchRepository.findById(saved.getId()).orElseThrow();
            assertThat(updated.getUpdatedAt()).isAfter(originalUpdatedAt);
            assertThat(updated.getCreatedAt()).isEqualTo(saved.getCreatedAt());
        }
    }

    @Nested
    @DisplayName("findByStateOrderByIdAsc")
    class FindByStateOrderByIdAsc {

        @Test
        @DisplayName("returns batches in given state ordered by id ascending")
        void returnsBatchesInStateOrderedByIdAsc() {
            Batch batch3 = createBatch("uuid:3", BatchState.PLANNED, "dk");
            Batch batch1 = createBatch("uuid:1", BatchState.PLANNED, "dk");
            Batch batch2 = createBatch("uuid:2", BatchState.RUNNING, "dk");

            batchRepository.save(batch3);
            batchRepository.save(batch1);
            batchRepository.save(batch2);
            entityManager.flush();

            List<Batch> planned = batchRepository.findByStateOrderByIdAsc(BatchState.PLANNED);

            assertThat(planned).hasSize(2);
            assertThat(planned.get(0).getId()).isLessThan(planned.get(1).getId());
            assertThat(planned).allMatch(b -> b.getState() == BatchState.PLANNED);
        }

        @Test
        @DisplayName("returns empty list when no batch in state")
        void returnsEmpty_whenNoBatchInState() {
            batchRepository.save(createBatch("uuid:1", BatchState.PLANNED, "dk"));
            entityManager.flush();

            List<Batch> failed = batchRepository.findByStateOrderByIdAsc(BatchState.FAILED);

            assertThat(failed).isEmpty();
        }
    }

    @Nested
    @DisplayName("failAllRunningBatches")
    class FailAllRunningBatches {

        @Test
        @DisplayName("sets all RUNNING batches to FAILED and appends log")
        void setsRunningToFailedAndAppendsLog() {
            Batch running1 = createBatch("uuid:1", BatchState.RUNNING, "dk");
            Batch running2 = createBatch("uuid:2", BatchState.RUNNING, "dk");
            Batch planned = createBatch("uuid:3", BatchState.PLANNED, "dk");

            running1.setLog("Initial log");
            batchRepository.saveAll(List.of(running1, running2, planned));
            entityManager.flush();
            entityManager.clear();

            int updated = batchRepository.failAllRunningBatches("Application restarted");

            assertThat(updated).isEqualTo(2);

            List<Batch> failed = batchRepository.findByStateOrderByIdAsc(BatchState.FAILED);
            assertThat(failed).hasSize(2);
            assertThat(failed).allMatch(b -> b.getLog() != null && b.getLog().contains("Application restarted"));

            Batch reloaded1 = batchRepository.findById(running1.getId()).orElseThrow();
            assertThat(reloaded1.getLog()).contains("Initial log");
            assertThat(reloaded1.getLog()).contains("Application restarted");

            Batch plannedBatch = batchRepository.findById(planned.getId()).orElseThrow();
            assertThat(plannedBatch.getState()).isEqualTo(BatchState.PLANNED);
        }

        @Test
        @DisplayName("returns 0 when no RUNNING batches")
        void returnsZero_whenNoRunningBatches() {
            batchRepository.save(createBatch("uuid:1", BatchState.PLANNED, "dk"));
            batchRepository.save(createBatch("uuid:2", BatchState.FAILED, "dk"));
            entityManager.flush();

            int updated = batchRepository.failAllRunningBatches("Application restarted");

            assertThat(updated).isEqualTo(0);
        }
    }
}
