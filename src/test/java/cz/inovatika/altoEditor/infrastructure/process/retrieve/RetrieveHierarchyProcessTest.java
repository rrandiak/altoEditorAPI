package cz.inovatika.altoEditor.infrastructure.process.retrieve;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import cz.inovatika.altoEditor.config.properties.BatchProperties;
import cz.inovatika.altoEditor.domain.enums.BatchPriority;
import cz.inovatika.altoEditor.domain.enums.BatchState;
import cz.inovatika.altoEditor.domain.enums.BatchType;
import cz.inovatika.altoEditor.domain.model.Batch;
import cz.inovatika.altoEditor.domain.model.DigitalObject;
import cz.inovatika.altoEditor.domain.service.AltoVersionService;
import cz.inovatika.altoEditor.domain.service.BatchService;
import cz.inovatika.altoEditor.domain.service.ObjectHierarchyService;
import cz.inovatika.altoEditor.infrastructure.kramerius.KrameriusService;
import cz.inovatika.altoEditor.infrastructure.kramerius.model.KrameriusObjectMetadata;

class RetrieveHierarchyProcessTest {

    @Test
    void processesRepeatedPagePidOnlyOnceAcrossWorkers() {
        String rootPid = "uuid:cb842dea-ebc4-11f0-a030-d6461869f154";
        String pagePid = "uuid:949778ec-7f0c-4f2a-b380-2405bbd72745";
        String instance = "k7-mzk";
        long userId = 1L;
        byte[] alto = "alto".getBytes();

        BatchService batchService = mock(BatchService.class);
        KrameriusService krameriusService = mock(KrameriusService.class);
        AltoVersionService altoVersionService = mock(AltoVersionService.class);
        ObjectHierarchyService objectHierarchyService = mock(ObjectHierarchyService.class);
        BatchProperties properties = new BatchProperties();
        properties.setWorkerThreads(3);

        Batch batch = Batch.builder().id(90540).type(BatchType.RETRIEVE_HIERARCHY)
                .priority(BatchPriority.MEDIUM).pid(rootPid).instance(instance).build();
        KrameriusObjectMetadata root = KrameriusObjectMetadata.builder()
                .pid(rootPid).model("periodicalitem").build();
        KrameriusObjectMetadata page = KrameriusObjectMetadata.builder()
                .pid(pagePid).model("page").parentPid(rootPid).build();

        when(batchService.getById(batch.getId())).thenReturn(batch);
        when(krameriusService.getObjectMetadata(rootPid, instance)).thenReturn(root);
        when(krameriusService.getPagesCount(rootPid, instance)).thenReturn(1);
        when(krameriusService.getChildrenMetadata(rootPid, instance)).thenReturn(List.of(page, page));
        when(krameriusService.getAltoBytes(pagePid, instance)).thenReturn(alto);
        when(objectHierarchyService.fetchAndStore(rootPid, instance, false))
                .thenReturn(DigitalObject.builder().pid(rootPid).build());
        when(objectHierarchyService.store(page, false))
                .thenReturn(DigitalObject.builder().pid(pagePid).build());

        new RetrieveHierarchyProcess(batchService, krameriusService, altoVersionService,
                objectHierarchyService, properties, userId, batch).run();

        verify(objectHierarchyService).store(page, false);
        verify(altoVersionService).updateOrCreateKrameriusVersion(pagePid, userId, alto, false);
        verify(batchService).setState(batch, BatchState.DONE);
    }
}
