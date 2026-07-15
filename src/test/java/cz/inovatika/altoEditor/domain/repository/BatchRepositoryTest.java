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
import org.springframework.data.domain.PageRequest;
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

    private Batch createStage(BatchType type, BatchState state, Integer parentBatchId, Integer stageOrder) {
        return Batch.builder()
                .type(type)
                .priority(BatchPriority.MEDIUM)
                .pid("uuid:pipeline-page")
                .state(state)
                .parentBatchId(parentBatchId)
                .stageOrder(stageOrder)
                .createdBy(testUser)
                .build();
    }

    private Batch claim(BatchType type) {
        return batchRepository
                .findClaimableByStateAndType(BatchState.PLANNED, type, PageRequest.of(0, 1))
                .stream().findFirst().orElse(null);
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
    @DisplayName("findClaimableByStateAndType (dependency-aware claim)")
    class FindClaimable {

        @Test
        @DisplayName("standalone PLANNED batch (no parent) is always claimable")
        void standaloneBatchIsClaimable() {
            batchRepository.save(createBatch("uuid:1", BatchState.PLANNED, "dk"));
            entityManager.flush();

            assertThat(claim(BatchType.GENERATE_SINGLE)).isNotNull();
        }

        @Test
        @DisplayName("pipeline child stages become claimable only after the previous stage is DONE")
        void pipelineStagesClaimableInOrder() {
            Batch parent = batchRepository.save(Batch.builder()
                    .type(BatchType.PIPELINE).priority(BatchPriority.MEDIUM)
                    .pid("uuid:root").state(BatchState.PLANNED).createdBy(testUser).build());
            Batch retrieve = batchRepository.save(createStage(BatchType.RETRIEVE_HIERARCHY, BatchState.PLANNED, parent.getId(), 0));
            Batch generate = batchRepository.save(createStage(BatchType.GENERATE_FOR_HIERARCHY, BatchState.PLANNED, parent.getId(), 1));
            Batch accept = batchRepository.save(createStage(BatchType.ACCEPT_VERSIONS, BatchState.PLANNED, parent.getId(), 2));
            entityManager.flush();

            // Only stage 0 is claimable initially
            assertThat(claim(BatchType.RETRIEVE_HIERARCHY)).extracting(Batch::getId).isEqualTo(retrieve.getId());
            assertThat(claim(BatchType.GENERATE_FOR_HIERARCHY)).isNull();
            assertThat(claim(BatchType.ACCEPT_VERSIONS)).isNull();

            // Finish stage 0 -> stage 1 claimable, stage 2 still blocked
            retrieve.setState(BatchState.DONE);
            batchRepository.save(retrieve);
            entityManager.flush();
            assertThat(claim(BatchType.GENERATE_FOR_HIERARCHY)).extracting(Batch::getId).isEqualTo(generate.getId());
            assertThat(claim(BatchType.ACCEPT_VERSIONS)).isNull();

            // Finish stage 1 -> stage 2 claimable
            generate.setState(BatchState.DONE);
            batchRepository.save(generate);
            entityManager.flush();
            assertThat(claim(BatchType.ACCEPT_VERSIONS)).extracting(Batch::getId).isEqualTo(accept.getId());
        }
    }

    @Nested
    @DisplayName("pipeline child queries")
    class PipelineChildQueries {

        @Test
        @DisplayName("findByParentBatchIdOrderByStageOrderAsc returns children in stage order")
        void findsChildrenInStageOrder() {
            Batch parent = batchRepository.save(Batch.builder()
                    .type(BatchType.PIPELINE).priority(BatchPriority.MEDIUM)
                    .pid("uuid:root").state(BatchState.PLANNED).createdBy(testUser).build());
            batchRepository.save(createStage(BatchType.ACCEPT_VERSIONS, BatchState.PLANNED, parent.getId(), 2));
            batchRepository.save(createStage(BatchType.RETRIEVE_HIERARCHY, BatchState.PLANNED, parent.getId(), 0));
            batchRepository.save(createStage(BatchType.GENERATE_FOR_HIERARCHY, BatchState.PLANNED, parent.getId(), 1));
            entityManager.flush();

            List<Batch> children = batchRepository.findByParentBatchIdOrderByStageOrderAsc(parent.getId());

            assertThat(children).extracting(Batch::getStageOrder).containsExactly(0, 1, 2);
            assertThat(children).extracting(Batch::getType).containsExactly(
                    BatchType.RETRIEVE_HIERARCHY, BatchType.GENERATE_FOR_HIERARCHY, BatchType.ACCEPT_VERSIONS);
        }

        @Test
        @DisplayName("findByTypeAndStateIn returns only pipelines in the given states")
        void findsPipelinesByState() {
            batchRepository.save(Batch.builder().type(BatchType.PIPELINE).priority(BatchPriority.MEDIUM)
                    .pid("uuid:a").state(BatchState.RUNNING).createdBy(testUser).build());
            batchRepository.save(Batch.builder().type(BatchType.PIPELINE).priority(BatchPriority.MEDIUM)
                    .pid("uuid:b").state(BatchState.DONE).createdBy(testUser).build());
            entityManager.flush();

            List<Batch> active = batchRepository.findByTypeAndStateIn(
                    BatchType.PIPELINE, List.of(BatchState.PLANNED, BatchState.RUNNING));

            assertThat(active).hasSize(1);
            assertThat(active.get(0).getState()).isEqualTo(BatchState.RUNNING);
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

            Batch runningBatch = batchRepository.findById(running2.getId()).orElseThrow();
            assertThat(runningBatch.getState()).isEqualTo(BatchState.FAILED);
        }

        @Test
        @DisplayName("returns 0 when no RUNNING batches")
        void returnsZero_whenNoRunningBatches() {
            batchRepository.save(createBatch("uuid:1", BatchState.DONE, "dk"));
            batchRepository.save(createBatch("uuid:2", BatchState.FAILED, "dk"));
            entityManager.flush();

            int updated = batchRepository.failAllRunningBatches("Application restarted");

            assertThat(updated).isEqualTo(0);
        }
    }
}
