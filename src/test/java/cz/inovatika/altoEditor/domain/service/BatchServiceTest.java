package cz.inovatika.altoEditor.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
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

@ExtendWith(MockitoExtension.class)
class BatchServiceTest {

    @Mock
    private BatchRepository batchRepository;

    @InjectMocks
    private BatchService batchService;

    private Batch testBatch;

    @BeforeEach
    void setUp() {
        testBatch = new Batch();
        testBatch.setId(1);
        testBatch.setPid("uuid:12345");
        testBatch.setState(BatchState.PLANNED);
        testBatch.setPriority(BatchPriority.MEDIUM);
        testBatch.setType(BatchType.GENERATE);
        testBatch.setInstance("dk");
    }

    @Test
    @DisplayName("Find waiting batches should return PLANNED batches ordered by ID")
    void findWaitingBatches_shouldReturnPlannedBatches() {
        // Given
        Batch batch1 = new Batch();
        batch1.setId(1);
        batch1.setState(BatchState.PLANNED);

        Batch batch2 = new Batch();
        batch2.setId(2);
        batch2.setState(BatchState.PLANNED);

        List<Batch> expectedBatches = List.of(batch1, batch2);
        when(batchRepository.findByStateOrderByIdAsc(BatchState.PLANNED))
                .thenReturn(expectedBatches);

        // When
        List<Batch> result = batchService.findWaitingBatches();

        // Then
        assertThat(result).hasSize(2);
        assertThat(result).isEqualTo(expectedBatches);
        verify(batchRepository).findByStateOrderByIdAsc(BatchState.PLANNED);
    }

    @Test
    @DisplayName("Find waiting batches should return empty list when no PLANNED batches")
    void findWaitingBatches_shouldReturnEmptyList_whenNoBatches() {
        // Given
        when(batchRepository.findByStateOrderByIdAsc(BatchState.PLANNED))
                .thenReturn(List.of());

        // When
        List<Batch> result = batchService.findWaitingBatches();

        // Then
        assertThat(result).isEmpty();
        verify(batchRepository).findByStateOrderByIdAsc(BatchState.PLANNED);
    }

    @Test
    @DisplayName("Find running batches should return RUNNING batches ordered by ID")
    void findRunningBatches_shouldReturnRunningBatches() {
        // Given
        Batch batch1 = new Batch();
        batch1.setId(1);
        batch1.setState(BatchState.RUNNING);

        Batch batch2 = new Batch();
        batch2.setId(2);
        batch2.setState(BatchState.RUNNING);

        List<Batch> expectedBatches = List.of(batch1, batch2);
        when(batchRepository.findByStateOrderByIdAsc(BatchState.RUNNING))
                .thenReturn(expectedBatches);

        // When
        List<Batch> result = batchService.findRunningBatches();

        // Then
        assertThat(result).hasSize(2);
        assertThat(result).isEqualTo(expectedBatches);
        verify(batchRepository).findByStateOrderByIdAsc(BatchState.RUNNING);
    }

    @Test
    @DisplayName("Set state should update batch state and save")
    void setState_shouldUpdateStateAndSave() {
        // Given
        when(batchRepository.save(testBatch)).thenReturn(testBatch);

        // When
        batchService.setState(testBatch, BatchState.RUNNING);

        // Then
        assertThat(testBatch.getState()).isEqualTo(BatchState.RUNNING);
        verify(batchRepository).save(testBatch);
    }

    @Test
    @DisplayName("Set state should handle state transitions correctly")
    void setState_shouldHandleStateTransitions() {
        // Given
        testBatch.setState(BatchState.PLANNED);
        when(batchRepository.save(testBatch)).thenReturn(testBatch);

        // When - transition through states
        batchService.setState(testBatch, BatchState.RUNNING);
        assertThat(testBatch.getState()).isEqualTo(BatchState.RUNNING);

        batchService.setState(testBatch, BatchState.DONE);
        assertThat(testBatch.getState()).isEqualTo(BatchState.DONE);

        // Then
        verify(batchRepository, times(2)).save(testBatch);
    }

    @Test
    @DisplayName("Set substate should update batch substate and save")
    void setSubstate_shouldUpdateSubstateAndSave() {
        // Given
        when(batchRepository.save(testBatch)).thenReturn(testBatch);

        // When
        batchService.setSubstate(testBatch, BatchSubstate.DOWNLOADING);

        // Then
        assertThat(testBatch.getSubstate()).isEqualTo(BatchSubstate.DOWNLOADING);
        verify(batchRepository).save(testBatch);
    }

    @Test
    @DisplayName("Set substate should handle substate transitions")
    void setSubstate_shouldHandleSubstateTransitions() {
        // Given
        when(batchRepository.save(testBatch)).thenReturn(testBatch);

        // When - transition through substates
        batchService.setSubstate(testBatch, BatchSubstate.DOWNLOADING);
        assertThat(testBatch.getSubstate()).isEqualTo(BatchSubstate.DOWNLOADING);

        batchService.setSubstate(testBatch, BatchSubstate.GENERATING);
        assertThat(testBatch.getSubstate()).isEqualTo(BatchSubstate.GENERATING);

        batchService.setSubstate(testBatch, BatchSubstate.SAVING);
        assertThat(testBatch.getSubstate()).isEqualTo(BatchSubstate.SAVING);

        // Then
        verify(batchRepository, times(3)).save(testBatch);
    }

    @Test
    @DisplayName("Set failed should update state to FAILED and set log")
    void setFailed_shouldUpdateStateAndLog() {
        // Given
        String reason = "Processing failed due to timeout";
        when(batchRepository.save(testBatch)).thenReturn(testBatch);

        // When
        batchService.setFailed(testBatch, reason);

        // Then
        assertThat(testBatch.getState()).isEqualTo(BatchState.FAILED);
        assertThat(testBatch.getLog()).isEqualTo(reason);
        verify(batchRepository).save(testBatch);
    }

    @Test
    @DisplayName("Set failed should overwrite existing log")
    void setFailed_shouldOverwriteExistingLog() {
        // Given
        testBatch.setLog("Previous log");
        String newReason = "New failure reason";
        when(batchRepository.save(testBatch)).thenReturn(testBatch);

        // When
        batchService.setFailed(testBatch, newReason);

        // Then
        assertThat(testBatch.getLog()).isEqualTo(newReason);
        verify(batchRepository).save(testBatch);
    }

    @Test
    @DisplayName("Search with all parameters should build correct specification")
    void search_shouldBuildCorrectSpecification() {
        // Given
        String pid = "uuid:12345";
        BatchState state = BatchState.RUNNING;
        BatchSubstate substate = BatchSubstate.DOWNLOADING;
        LocalDateTime createdAfter = LocalDateTime.now().minusDays(7);
        LocalDateTime createdBefore = LocalDateTime.now();
        LocalDateTime updatedAfter = LocalDateTime.now().minusDays(1);
        LocalDateTime updatedBefore = LocalDateTime.now();
        BatchPriority priority = BatchPriority.HIGH;
        BatchType type = BatchType.GENERATE;
        String instanceId = "dk";
        Pageable pageable = PageRequest.of(0, 10);

        Page<Batch> expectedPage = new PageImpl<>(List.of(testBatch));
        when(batchRepository.findAll(ArgumentMatchers.<Specification<Batch>>any(), eq(pageable)))
                .thenReturn(expectedPage);

        // When
        Page<Batch> result = batchService.search(
                pid, state, substate, createdAfter, createdBefore,
                updatedAfter, updatedBefore, priority, type, instanceId, pageable);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0)).isEqualTo(testBatch);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Specification<Batch>> specCaptor = ArgumentCaptor
                .forClass((Class<Specification<Batch>>) (Class<?>) Specification.class);
        verify(batchRepository).findAll(specCaptor.capture(), eq(pageable));
    }

    @Test
    @DisplayName("Search with null parameters should handle gracefully")
    void search_shouldHandleNullParameters() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);
        Page<Batch> expectedPage = new PageImpl<>(List.of(testBatch));
        when(batchRepository.findAll(ArgumentMatchers.<Specification<Batch>>any(), eq(pageable)))
                .thenReturn(expectedPage);

        // When
        Page<Batch> result = batchService.search(
                null, null, null, null, null, null, null, null, null, null, pageable);

        // Then
        assertThat(result).isNotNull();
        verify(batchRepository).findAll(
                ArgumentMatchers.<Specification<Batch>>any(), eq(pageable));
    }

    @Test
    @DisplayName("Search with only PID should filter by PID")
    void search_shouldFilterByPidOnly() {
        // Given
        String pid = "uuid:12345";
        Pageable pageable = PageRequest.of(0, 10);
        Page<Batch> expectedPage = new PageImpl<>(List.of(testBatch));
        when(batchRepository.findAll(ArgumentMatchers.<Specification<Batch>>any(), eq(pageable)))
                .thenReturn(expectedPage);

        // When
        Page<Batch> result = batchService.search(
                pid, null, null, null, null, null, null, null, null, null, pageable);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        verify(batchRepository).findAll(
                ArgumentMatchers.<Specification<Batch>>any(), eq(pageable));
    }

    @Test
    @DisplayName("Search with pagination should respect page size")
    void search_shouldRespectPagination() {
        // Given
        Pageable pageable = PageRequest.of(0, 5);
        List<Batch> batches = List.of(testBatch);
        Page<Batch> expectedPage = new PageImpl<>(batches, pageable, 1);
        when(batchRepository.findAll(ArgumentMatchers.<Specification<Batch>>any(), eq(pageable)))
                .thenReturn(expectedPage);

        // When
        Page<Batch> result = batchService.search(
                null, null, null, null, null, null, null, null, null, null, pageable);

        // Then
        assertThat(result.getSize()).isEqualTo(5);
        assertThat(result.getNumber()).isEqualTo(0);
        verify(batchRepository).findAll(
                ArgumentMatchers.<Specification<Batch>>any(), eq(pageable));
    }

    @Test
    @DisplayName("Search should return empty page when no results")
    void search_shouldReturnEmptyPage_whenNoResults() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);
        Page<Batch> expectedPage = new PageImpl<>(List.of());
        when(batchRepository.findAll(ArgumentMatchers.<Specification<Batch>>any(), eq(pageable)))
                .thenReturn(expectedPage);

        // When
        Page<Batch> result = batchService.search(
                "uuid:nonexistent", null, null, null, null, null, null, null, null, null, pageable);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isEqualTo(0);
        verify(batchRepository).findAll(
                ArgumentMatchers.<Specification<Batch>>any(), eq(pageable));
    }

    @Test
    @DisplayName("Search with date range should filter correctly")
    void search_shouldFilterByDateRange() {
        // Given
        LocalDateTime createdAfter = LocalDateTime.now().minusDays(7);
        LocalDateTime createdBefore = LocalDateTime.now();
        Pageable pageable = PageRequest.of(0, 10);
        Page<Batch> expectedPage = new PageImpl<>(List.of(testBatch));
        when(batchRepository.findAll(ArgumentMatchers.<Specification<Batch>>any(), eq(pageable)))
                .thenReturn(expectedPage);

        // When
        Page<Batch> result = batchService.search(
                null, null, null, createdAfter, createdBefore, null, null, null, null, null, pageable);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        verify(batchRepository).findAll(
                ArgumentMatchers.<Specification<Batch>>any(), eq(pageable));
    }

    @Test
    @DisplayName("Search with multiple criteria should combine filters")
    void search_shouldCombineMultipleFilters() {
        // Given
        String pid = "uuid:12345";
        BatchState state = BatchState.RUNNING;
        BatchPriority priority = BatchPriority.HIGH;
        String instanceId = "dk";
        Pageable pageable = PageRequest.of(0, 10);
        Page<Batch> expectedPage = new PageImpl<>(List.of(testBatch));
        when(batchRepository.findAll(ArgumentMatchers.<Specification<Batch>>any(), eq(pageable)))
                .thenReturn(expectedPage);

        // When
        Page<Batch> result = batchService.search(
                pid, state, null, null, null, null, null, priority, null, instanceId, pageable);

        // Then
        assertThat(result).isNotNull();
        verify(batchRepository).findAll(
                ArgumentMatchers.<Specification<Batch>>any(), eq(pageable));
    }
}