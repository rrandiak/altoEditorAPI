package cz.inovatika.altoEditor.infrastructure.process.accept;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.fasterxml.jackson.databind.ObjectMapper;

import cz.inovatika.altoEditor.config.properties.BatchProperties;
import cz.inovatika.altoEditor.domain.adapter.PidAdapter;
import cz.inovatika.altoEditor.domain.enums.BatchPriority;
import cz.inovatika.altoEditor.domain.enums.BatchState;
import cz.inovatika.altoEditor.domain.enums.BatchType;
import cz.inovatika.altoEditor.domain.model.Batch;
import cz.inovatika.altoEditor.domain.service.AltoVersionService;
import cz.inovatika.altoEditor.domain.service.BatchService;
import cz.inovatika.altoEditor.domain.service.ObjectHierarchyService;
import cz.inovatika.altoEditor.domain.service.container.AltoVersionUploadContent;
import cz.inovatika.altoEditor.infrastructure.kramerius.KrameriusService;

/**
 * Unit tests for the parallelized {@link AcceptVersionsProcess}. Verifies that a
 * multi-page accept batch uploads once per page, flips each version to ACTIVE via
 * the batch overload ({@code refreshHierarchyStats=false}), and refreshes ancestor
 * page counts exactly once after the parallel phase (never per page).
 */
class AcceptVersionsProcessTest {

    @Mock
    private BatchService batchService;
    @Mock
    private AltoVersionService altoVersionService;
    @Mock
    private KrameriusService krameriusService;
    @Mock
    private ObjectHierarchyService objectHierarchyService;

    private BatchProperties batchProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final List<String> PIDS = List.of(
            "uuid:00000000-0000-0000-0000-000000000001",
            "uuid:00000000-0000-0000-0000-000000000002",
            "uuid:00000000-0000-0000-0000-000000000003",
            "uuid:00000000-0000-0000-0000-000000000004",
            "uuid:00000000-0000-0000-0000-000000000005");

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        batchProperties = new BatchProperties();
        batchProperties.setWorkerThreads(4);
    }

    private Batch batch(String targetPid) {
        return Batch.builder()
                .id(1)
                .type(BatchType.ACCEPT_VERSIONS)
                .priority(BatchPriority.MEDIUM)
                .createdAt(LocalDateTime.now())
                .pid(targetPid)
                .state(BatchState.PLANNED)
                .build();
    }

    private AcceptVersionsProcess process(Batch batch) {
        return new AcceptVersionsProcess(batchService, altoVersionService, krameriusService,
                objectHierarchyService, batchProperties, objectMapper, batch);
    }

    @Test
    @DisplayName("uploads once per page, accepts each without per-page refresh, refreshes ancestors once, marks DONE")
    void parallelAccept_uploadsPerPage_refreshesAncestorsOnce() {
        Batch batch = batch("uuid:00000000-0000-0000-0000-0000000000ff");
        when(batchService.getById(1)).thenReturn(batch);

        List<Integer> versionIds = List.of(10, 11, 12, 13, 14);
        when(altoVersionService.findVersionIdsByFilter(any())).thenReturn(versionIds);
        for (int i = 0; i < versionIds.size(); i++) {
            String pid = PIDS.get(i);
            when(altoVersionService.getAltoVersionUploadContent(versionIds.get(i)))
                    .thenReturn(AltoVersionUploadContent.builder()
                            .pid(pid)
                            .altoContent(("alto-" + pid).getBytes())
                            .ocrContent(("ocr-" + pid).getBytes())
                            .build());
        }

        process(batch).run();

        // (a) every version accepted via the batch overload (no per-page ancestor refresh)
        for (Integer versionId : versionIds) {
            verify(altoVersionService).accept(versionId, false);
        }
        verify(altoVersionService, never()).accept(anyInt());

        // (b) uploadAltoOcr called exactly once per page
        for (int i = 0; i < versionIds.size(); i++) {
            verify(krameriusService).uploadAltoOcr(eq(PIDS.get(i)), any(), any());
        }

        // (c) ancestor page counts refreshed exactly once, for all accepted pages
        ArgumentCaptor<List<UUID>> captor = ArgumentCaptor.forClass(List.class);
        verify(objectHierarchyService, times(1)).refreshPageCountsForAcceptedPages(captor.capture());
        List<UUID> expected = new ArrayList<>();
        for (String pid : PIDS) {
            expected.add(PidAdapter.toUuid(pid));
        }
        assertThat(captor.getValue()).containsExactlyInAnyOrderElementsOf(expected);

        // (d) never the per-page ancestor walk
        verify(objectHierarchyService, never()).refreshPageCountsForAncestors(any());

        // rebuild targets all accepted pages; reindex uses the hierarchy root (recursive)
        ArgumentCaptor<List<String>> rebuildPids = ArgumentCaptor.forClass(List.class);
        verify(krameriusService).rebuildAndReindex(rebuildPids.capture(), eq(batch.getPid()));
        assertThat(rebuildPids.getValue()).containsExactlyInAnyOrderElementsOf(PIDS);
        verify(batchService).setState(batch, BatchState.DONE);
    }

    @Test
    @DisplayName("without a batch pid, plans per-object indexing for the accepted pids")
    void parallelAccept_noBatchPid_plansObjectIndexing() {
        Batch batch = batch(null);
        when(batchService.getById(1)).thenReturn(batch);

        List<Integer> versionIds = List.of(10, 11);
        when(altoVersionService.findVersionIdsByFilter(any())).thenReturn(versionIds);
        for (int i = 0; i < versionIds.size(); i++) {
            String pid = PIDS.get(i);
            when(altoVersionService.getAltoVersionUploadContent(versionIds.get(i)))
                    .thenReturn(AltoVersionUploadContent.builder().pid(pid)
                            .altoContent(new byte[] {1}).ocrContent(new byte[] {2}).build());
        }

        process(batch).run();

        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        verify(krameriusService).rebuildAndReindex(captor.capture(), isNull());
        assertThat(captor.getValue()).containsExactlyInAnyOrder(PIDS.get(0), PIDS.get(1));
        verify(batchService).setState(batch, BatchState.DONE);
    }

    @Test
    @DisplayName("on a page failure, marks the batch failed and does not refresh ancestors or index")
    void parallelAccept_onFailure_marksBatchFailed() {
        Batch batch = batch("uuid:00000000-0000-0000-0000-0000000000ff");
        when(batchService.getById(1)).thenReturn(batch);

        List<Integer> versionIds = List.of(10, 11, 12);
        when(altoVersionService.findVersionIdsByFilter(any())).thenReturn(versionIds);
        when(altoVersionService.getAltoVersionUploadContent(anyInt()))
                .thenReturn(AltoVersionUploadContent.builder().pid(PIDS.get(0))
                        .altoContent(new byte[] {1}).ocrContent(new byte[] {2}).build());
        when(altoVersionService.getAltoVersionUploadContent(11))
                .thenThrow(new RuntimeException("boom"));

        process(batch).run();

        verify(batchService).setFailed(eq(batch), any());
        verify(objectHierarchyService, never()).refreshPageCountsForAcceptedPages(any());
        verify(krameriusService, never()).rebuildAndReindex(any(), any());
        verify(batchService, never()).setState(batch, BatchState.DONE);
    }
}
