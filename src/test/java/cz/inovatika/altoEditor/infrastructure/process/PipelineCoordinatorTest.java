package cz.inovatika.altoEditor.infrastructure.process;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import cz.inovatika.altoEditor.domain.enums.BatchState;
import cz.inovatika.altoEditor.domain.enums.BatchType;
import cz.inovatika.altoEditor.domain.model.Batch;
import cz.inovatika.altoEditor.domain.repository.BatchRepository;
import cz.inovatika.altoEditor.domain.service.BatchService;

@ExtendWith(MockitoExtension.class)
class PipelineCoordinatorTest {

    @Mock
    private BatchRepository batchRepository;
    @Mock
    private BatchService batchService;

    @InjectMocks
    private PipelineCoordinator coordinator;

    private Batch pipeline(int id, BatchState state) {
        return Batch.builder().id(id).type(BatchType.PIPELINE).state(state).build();
    }

    private Batch stage(int id, BatchType type, BatchState state, int stageOrder) {
        return Batch.builder().id(id).type(type).state(state).parentBatchId(100).stageOrder(stageOrder).build();
    }

    private void givenPipelineWithChildren(Batch parent, List<Batch> children) {
        when(batchRepository.findByTypeAndStateIn(eq(BatchType.PIPELINE), any())).thenReturn(List.of(parent));
        when(batchRepository.findByParentBatchIdOrderByStageOrderAsc(parent.getId())).thenReturn(children);
    }

    @Test
    @DisplayName("a failed stage fails the parent and skips remaining PLANNED children")
    void failPropagation() {
        Batch parent = pipeline(100, BatchState.RUNNING);
        Batch done = stage(1, BatchType.RETRIEVE_HIERARCHY, BatchState.DONE, 0);
        Batch failed = stage(2, BatchType.GENERATE_FOR_HIERARCHY, BatchState.FAILED, 1);
        Batch planned = stage(3, BatchType.ACCEPT_VERSIONS, BatchState.PLANNED, 2);
        givenPipelineWithChildren(parent, List.of(done, failed, planned));

        coordinator.reconcile();

        verify(batchService).setFailed(eq(planned), any());
        verify(batchService).setFailed(eq(parent), any());
        verify(batchService, never()).setFailed(eq(done), any());
        verify(batchService, never()).setState(any(), eq(BatchState.DONE));
    }

    @Test
    @DisplayName("all children DONE completes the parent")
    void allDoneCompletesParent() {
        Batch parent = pipeline(100, BatchState.RUNNING);
        givenPipelineWithChildren(parent, List.of(
                stage(1, BatchType.RETRIEVE_HIERARCHY, BatchState.DONE, 0),
                stage(2, BatchType.GENERATE_FOR_HIERARCHY, BatchState.DONE, 1)));

        coordinator.reconcile();

        verify(batchService).setState(parent, BatchState.DONE);
    }

    @Test
    @DisplayName("a started stage promotes a PLANNED parent to RUNNING")
    void startedPromotesParentToRunning() {
        Batch parent = pipeline(100, BatchState.PLANNED);
        givenPipelineWithChildren(parent, List.of(
                stage(1, BatchType.RETRIEVE_HIERARCHY, BatchState.RUNNING, 0),
                stage(2, BatchType.GENERATE_FOR_HIERARCHY, BatchState.PLANNED, 1)));

        coordinator.reconcile();

        verify(batchService).setState(parent, BatchState.RUNNING);
    }

    @Test
    @DisplayName("nothing started yet leaves the parent untouched")
    void noneStartedLeavesParentUntouched() {
        Batch parent = pipeline(100, BatchState.PLANNED);
        givenPipelineWithChildren(parent, List.of(
                stage(1, BatchType.RETRIEVE_HIERARCHY, BatchState.PLANNED, 0),
                stage(2, BatchType.GENERATE_FOR_HIERARCHY, BatchState.PLANNED, 1)));

        coordinator.reconcile();

        verify(batchService, never()).setState(any(), any());
        verify(batchService, never()).setFailed(any(), any());
    }
}
