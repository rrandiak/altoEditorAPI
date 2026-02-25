package cz.inovatika.altoEditor.infrastructure.process.retrieve;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cz.inovatika.altoEditor.config.properties.BatchProperties;
import cz.inovatika.altoEditor.domain.enums.BatchState;
import cz.inovatika.altoEditor.domain.enums.Model;
import cz.inovatika.altoEditor.domain.model.Batch;
import cz.inovatika.altoEditor.domain.model.DigitalObject;
import cz.inovatika.altoEditor.domain.service.AltoVersionService;
import cz.inovatika.altoEditor.domain.service.BatchService;
import cz.inovatika.altoEditor.domain.service.ObjectHierarchyService;
import cz.inovatika.altoEditor.infrastructure.kramerius.KrameriusService;
import cz.inovatika.altoEditor.infrastructure.kramerius.model.KrameriusObjectMetadata;
import cz.inovatika.altoEditor.infrastructure.process.templates.BatchProcess;

public class RetrieveHierarchyProcess extends BatchProcess {

    private static final Logger LOGGER = LoggerFactory.getLogger(RetrieveHierarchyProcess.class);

    private static final int PROGRESS_UPDATE_INTERVAL = 100;

    private final BatchService batchService;
    private final KrameriusService krameriusService;
    private final AltoVersionService altoVersionService;
    private final ObjectHierarchyService objectHierarchyService;
    private final BatchProperties batchProperties;

    private final Long krameriusUserId;

    public RetrieveHierarchyProcess(
            BatchService batchService,
            KrameriusService krameriusService,
            AltoVersionService altoVersionService,
            ObjectHierarchyService objectHierarchyService,
            BatchProperties batchProperties,
            Long krameriusUserId,
            Batch batch) {

        super(batch.getId(), batch.getPriority(), batch.getCreatedAt());
        this.batchService = batchService;
        this.krameriusService = krameriusService;
        this.altoVersionService = altoVersionService;
        this.objectHierarchyService = objectHierarchyService;
        this.batchProperties = batchProperties;
        this.krameriusUserId = krameriusUserId;
    }

    @Override
    public void run() {
        Batch batch = batchService.getById(batchId);
        String instance = batch.getInstance();
        String originPid = batch.getPid();

        ExecutorService executor = Executors.newFixedThreadPool(batchProperties.getWorkerThreads());
        CompletionService<Integer> completionService = new ExecutorCompletionService<>(executor);

        try {
            batchService.setState(batch, BatchState.RUNNING);
            batchService.setProcessedItemCount(batch, 0);

            KrameriusObjectMetadata root = krameriusService.getObjectMetadata(originPid, instance);
            if (root == null) {
                batchService.setFailed(batch, "Object " + originPid + " not found in instance " + instance);
                return;
            }

            // 1. Producer: gather all metadata items
            List<KrameriusObjectMetadata> allItems = expandHierarchy(root, instance);
            int totalTasks = allItems.size();
            batchService.setEstimatedItemCount(batch, totalTasks);

            // 2. Submit tasks
            for (KrameriusObjectMetadata item : allItems) {
                completionService.submit(() -> processItem(item, instance, originPid));
            }

            // 3. Collect results
            int processed = 0;
            for (int i = 0; i < totalTasks; i++) {
                Future<Integer> future = completionService.take();
                processed += future.get();
                if (processed % PROGRESS_UPDATE_INTERVAL == 0 || processed == totalTasks) {
                    batchService.setProcessedItemCount(batch, processed);
                }
            }

            batchService.setProcessedItemCount(batch, processed);
            batchService.setState(batch, BatchState.DONE);

        } catch (Exception ex) {
            LOGGER.error("RetrieveHierarchyProcess batch {} failed: {}", batchId, ex.getMessage(), ex);

            try {
                batchService.setFailed(batch, ex.getMessage());
            } catch (Exception ex2) {
                LOGGER.error("RetrieveHierarchyProcess batch {} failed to set failed state: {}", batchId, ex2.getMessage(), ex2);
            }
        } finally {
            executor.shutdown();
        }
    }

    private List<KrameriusObjectMetadata> expandHierarchy(KrameriusObjectMetadata root, String instance) {
        List<KrameriusObjectMetadata> result = new ArrayList<>();
        Queue<KrameriusObjectMetadata> q = new LinkedList<>();
        q.add(root);

        while (!q.isEmpty()) {
            KrameriusObjectMetadata curr = q.poll();
            Model model = Model.fromModelName(curr.getModel());

            if (model == null || model.shouldIgnoreForHierarchyRetrieval()) {
                continue;
            }

            if (model != Model.COLLECTION) {
                result.add(curr);
            }

            List<KrameriusObjectMetadata> children = krameriusService.getChildrenMetadata(curr.getPid(), instance);
            q.addAll(children);
        }
        return result;
    }

    private int processItem(KrameriusObjectMetadata m, String instance, String originPid) {
        boolean isOrigin = m.getPid().equals(originPid);

        DigitalObject obj = isOrigin
                ? objectHierarchyService.fetchAndStore(m.getPid(), instance)
                : objectHierarchyService.store(m);

        if (Model.PAGE.isModel(m.getModel())) {
            byte[] altoBytes = krameriusService.getAltoBytes(obj.getPid(), instance);
            if (altoBytes != null) {
                altoVersionService.updateOrCreateKrameriusVersion(obj.getPid(), krameriusUserId, altoBytes);
            }
        }

        return 1;
    }
}