package cz.inovatika.altoEditor.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import cz.inovatika.altoEditor.domain.enums.BatchPriority;
import cz.inovatika.altoEditor.domain.enums.BatchState;
import cz.inovatika.altoEditor.domain.enums.BatchSubstate;
import cz.inovatika.altoEditor.domain.enums.BatchType;
import cz.inovatika.altoEditor.domain.model.Batch;
import cz.inovatika.altoEditor.domain.repository.BatchRepository;
import cz.inovatika.altoEditor.exception.BatchNotFoundException;

@ExtendWith(MockitoExtension.class)
class BatchServiceTest {

    @Mock
    private BatchRepository repository;

    @InjectMocks
    private BatchService service;

    private Batch batch;

    @BeforeEach
    void setUp() {
        batch = Batch.builder()
                .id(1)
                .pid("uuid:12345")
                .state(BatchState.PLANNED)
                .priority(BatchPriority.MEDIUM)
                .type(BatchType.GENERATE_SINGLE)
                .instance("dk")
                .build();
    }

    @Nested
    @DisplayName("getById")
    class GetById {

        @Test
        @DisplayName("returns batch when found")
        void returnsBatch_whenFound() {
            when(repository.findById(1)).thenReturn(Optional.of(batch));

            Batch result = service.getById(1);

            assertThat(result).isEqualTo(batch);
            verify(repository).findById(1);
        }

        @Test
        @DisplayName("throws BatchNotFoundException when not found")
        void throws_whenNotFound() {
            when(repository.findById(999)).thenReturn(Optional.empty());

            assertThrows(BatchNotFoundException.class, () -> service.getById(999));
            verify(repository).findById(999);
        }
    }

    @Nested
    @DisplayName("findWaitingBatches")
    class FindWaitingBatches {

        @Test
        @DisplayName("returns PLANNED batches ordered by id")
        void returnsPlannedBatchesOrderedById() {
            List<Batch> planned = List.of(
                    Batch.builder().id(1).state(BatchState.PLANNED).build(),
                    Batch.builder().id(2).state(BatchState.PLANNED).build());
            when(repository.findByStateOrderByIdAsc(BatchState.PLANNED)).thenReturn(planned);

            List<Batch> result = service.findWaitingBatches();

            assertThat(result).hasSize(2);
            assertThat(result).isEqualTo(planned);
            verify(repository).findByStateOrderByIdAsc(BatchState.PLANNED);
        }

        @Test
        @DisplayName("returns empty list when no PLANNED batches")
        void returnsEmpty_whenNone() {
            when(repository.findByStateOrderByIdAsc(BatchState.PLANNED)).thenReturn(List.of());

            List<Batch> result = service.findWaitingBatches();

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("findRunningBatches")
    class FindRunningBatches {

        @Test
        @DisplayName("returns RUNNING batches ordered by id")
        void returnsRunningBatchesOrderedById() {
            List<Batch> running = List.of(
                    Batch.builder().id(1).state(BatchState.RUNNING).build(),
                    Batch.builder().id(2).state(BatchState.RUNNING).build());
            when(repository.findByStateOrderByIdAsc(BatchState.RUNNING)).thenReturn(running);

            List<Batch> result = service.findRunningBatches();

            assertThat(result).hasSize(2);
            assertThat(result).isEqualTo(running);
            verify(repository).findByStateOrderByIdAsc(BatchState.RUNNING);
        }
    }

    @Nested
    @DisplayName("setState")
    class SetState {

        @Test
        @DisplayName("updates state and saves")
        void updatesStateAndSaves() {
            when(repository.save(batch)).thenReturn(batch);

            service.setState(batch, BatchState.RUNNING);

            assertThat(batch.getState()).isEqualTo(BatchState.RUNNING);
            verify(repository).save(batch);
        }

        @Test
        @DisplayName("saves on each state transition")
        void savesOnEachTransition() {
            when(repository.save(batch)).thenReturn(batch);

            service.setState(batch, BatchState.RUNNING);
            service.setState(batch, BatchState.DONE);

            assertThat(batch.getState()).isEqualTo(BatchState.DONE);
            verify(repository, times(2)).save(batch);
        }
    }

    @Nested
    @DisplayName("setSubstate")
    class SetSubstate {

        @Test
        @DisplayName("updates substate and saves")
        void updatesSubstateAndSaves() {
            when(repository.save(batch)).thenReturn(batch);

            service.setSubstate(batch, BatchSubstate.DOWNLOADING);

            assertThat(batch.getSubstate()).isEqualTo(BatchSubstate.DOWNLOADING);
            verify(repository).save(batch);
        }

        @Test
        @DisplayName("saves on each substate change")
        void savesOnEachChange() {
            when(repository.save(batch)).thenReturn(batch);

            service.setSubstate(batch, BatchSubstate.DOWNLOADING);
            service.setSubstate(batch, BatchSubstate.GENERATING);

            assertThat(batch.getSubstate()).isEqualTo(BatchSubstate.GENERATING);
            verify(repository, times(2)).save(batch);
        }
    }

    @Nested
    @DisplayName("setFailed")
    class SetFailed {

        @Test
        @DisplayName("sets state to FAILED and log message, then saves")
        void setsFailedStateAndLog() {
            String reason = "Processing failed due to timeout";
            when(repository.save(batch)).thenReturn(batch);

            service.setFailed(batch, reason);

            assertThat(batch.getState()).isEqualTo(BatchState.FAILED);
            assertThat(batch.getLog()).isEqualTo(reason);
            verify(repository).save(batch);
        }

        @Test
        @DisplayName("overwrites existing log")
        void overwritesExistingLog() {
            batch.setLog("Previous log");
            when(repository.save(batch)).thenReturn(batch);

            service.setFailed(batch, "New failure reason");

            assertThat(batch.getLog()).isEqualTo("New failure reason");
        }
    }

    @Nested
    @DisplayName("setEstimatedItemCount")
    class SetEstimatedItemCount {

        @Test
        @DisplayName("sets estimated item count and saves")
        void setsCountAndSaves() {
            when(repository.save(batch)).thenReturn(batch);

            service.setEstimatedItemCount(batch, 100);

            assertThat(batch.getEstimatedItemCount()).isEqualTo(100);
            verify(repository).save(batch);
        }
    }

    @Nested
    @DisplayName("setProcessedItemCount")
    class SetProcessedItemCount {

        @Test
        @DisplayName("sets processed item count and saves")
        void setsCountAndSaves() {
            when(repository.save(batch)).thenReturn(batch);

            service.setProcessedItemCount(batch, 50);

            assertThat(batch.getProcessedItemCount()).isEqualTo(50);
            verify(repository).save(batch);
        }
    }

    @Nested
    @DisplayName("search")
    @SuppressWarnings("unchecked")
    class Search {

        @Test
        @DisplayName("delegates to repository with specification and pageable")
        void delegatesToRepository() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<Batch> expectedPage = new PageImpl<>(List.of(batch));
            when(repository.findAll(isA(Specification.class), eq(pageable))).thenReturn(expectedPage);

            Page<Batch> result = service.search(
                    "uuid:12345", BatchState.RUNNING, BatchSubstate.DOWNLOADING,
                    LocalDateTime.now().minusDays(7), LocalDateTime.now(),
                    LocalDateTime.now().minusDays(1), LocalDateTime.now(),
                    BatchPriority.HIGH, BatchType.GENERATE_SINGLE, "dk",
                    pageable);

            assertThat(result).isNotNull();
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0)).isEqualTo(batch);
            verify(repository).findAll(isA(Specification.class), eq(pageable));
        }

        @Test
        @DisplayName("handles all null filters")
        void handlesNullFilters() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<Batch> expectedPage = new PageImpl<>(List.of());
            when(repository.findAll(isA(Specification.class), eq(pageable))).thenReturn(expectedPage);

            Page<Batch> result = service.search(
                    null, null, null, null, null, null, null, null, null, null, pageable);

            assertThat(result).isNotNull();
            verify(repository).findAll(isA(Specification.class), eq(pageable));
        }

        @Test
        @DisplayName("returns empty page when no results")
        void returnsEmptyPage_whenNoResults() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<Batch> emptyPage = new PageImpl<>(List.of(), pageable, 0);
            when(repository.findAll(isA(Specification.class), eq(pageable))).thenReturn(emptyPage);

            Page<Batch> result = service.search(
                    "uuid:nonexistent", null, null, null, null, null, null, null, null, null, pageable);

            assertThat(result.getContent()).isEmpty();
            assertThat(result.getTotalElements()).isEqualTo(0);
        }

        @Test
        @DisplayName("respects pageable size and number")
        void respectsPagination() {
            Pageable pageable = PageRequest.of(1, 5);
            Page<Batch> expectedPage = new PageImpl<>(List.of(batch), pageable, 1);
            when(repository.findAll(isA(Specification.class), eq(pageable))).thenReturn(expectedPage);

            Page<Batch> result = service.search(
                    null, null, null, null, null, null, null, null, null, null, pageable);

            assertThat(result.getSize()).isEqualTo(5);
            assertThat(result.getNumber()).isEqualTo(1);
        }
    }
}
