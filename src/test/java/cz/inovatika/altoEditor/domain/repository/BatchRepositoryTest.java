package cz.inovatika.altoEditor.domain.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.TestPropertySource;

import cz.inovatika.altoEditor.domain.enums.BatchPriority;
import cz.inovatika.altoEditor.domain.enums.BatchState;
import cz.inovatika.altoEditor.domain.enums.BatchType;
import cz.inovatika.altoEditor.domain.model.Batch;

@DataJpaTest
@TestPropertySource(properties = "altoeditor.home=src/test/resources")
class BatchRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private BatchRepository batchRepository;

    private Batch createBatch(String pid, BatchState state, String instance) {
        Batch batch = new Batch();
        batch.setPid(pid);
        batch.setState(state);
        batch.setInstance(instance);
        batch.setPriority(BatchPriority.MEDIUM);
        batch.setType(BatchType.GENERATE);
        return batch;
    }

    @BeforeEach
    void setUp() {
        batchRepository.deleteAll();
    }

    @Test
    void whenSaveBatch_thenCreatedAtAndUpdatedAtAreSet() {
        // Given
        Batch batch = createBatch("uuid:12345", BatchState.PLANNED, "dk");

        // When
        Batch saved = batchRepository.save(batch);
        entityManager.flush();

        // Then
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getCreatedAt()).isEqualTo(saved.getUpdatedAt());
    }

    @Test
    void whenUpdateBatch_thenUpdatedAtChanges() throws InterruptedException {
        // Given
        Batch batch = createBatch("uuid:12345", BatchState.PLANNED, "dk");
        Batch saved = batchRepository.save(batch);
        entityManager.flush();
        LocalDateTime originalUpdatedAt = saved.getUpdatedAt();

        // Wait a bit to ensure time difference
        Thread.sleep(10);

        // When
        saved.setState(BatchState.RUNNING);
        batchRepository.save(saved);
        entityManager.flush();

        // Then
        Batch updated = batchRepository.findById(saved.getId()).orElseThrow();
        assertThat(updated.getUpdatedAt()).isAfter(originalUpdatedAt);
        assertThat(updated.getCreatedAt()).isEqualTo(saved.getCreatedAt());
    }

    @Test
    void findByStateOrderByIdAsc_shouldReturnBatchesInOrder() {
        // Given
        Batch batch1 = createBatch("uuid:1", BatchState.PLANNED, "dk");
        Batch batch2 = createBatch("uuid:2", BatchState.RUNNING, "dk");
        Batch batch3 = createBatch("uuid:3", BatchState.PLANNED, "dk");

        batchRepository.save(batch3);
        batchRepository.save(batch1);
        batchRepository.save(batch2);
        entityManager.flush();

        // When
        List<Batch> planned = batchRepository.findByStateOrderByIdAsc(BatchState.PLANNED);

        // Then
        assertThat(planned).hasSize(2);
        assertThat(planned.get(0).getId()).isLessThan(planned.get(1).getId());
    }

    @Test
    void failAllRunningBatches_shouldUpdateRunningBatches() {
        // Given
        Batch running1 = createBatch("uuid:1", BatchState.RUNNING, "dk");
        Batch running2 = createBatch("uuid:2", BatchState.RUNNING, "dk");
        Batch planned = createBatch("uuid:3", BatchState.PLANNED, "dk");

        running1.setLog("Initial log");
        batchRepository.saveAll(List.of(running1, running2, planned));
        entityManager.flush();
        entityManager.clear(); // Clear persistence context

        // When
        int updated = batchRepository.failAllRunningBatches("Application restarted");

        // Then
        assertThat(updated).isEqualTo(2);

        List<Batch> failed = batchRepository.findByStateOrderByIdAsc(BatchState.FAILED);
        assertThat(failed).hasSize(2);
        assertThat(failed).allMatch(b -> b.getLog().contains("Application restarted"));

        // Check that log was appended, not replaced
        Batch batch1 = batchRepository.findById(running1.getId()).orElseThrow();
        assertThat(batch1.getLog()).contains("Initial log");
        assertThat(batch1.getLog()).contains("Application restarted");

        // Check planned batch was not affected
        Batch plannedBatch = batchRepository.findById(planned.getId()).orElseThrow();
        assertThat(plannedBatch.getState()).isEqualTo(BatchState.PLANNED);
    }
}
