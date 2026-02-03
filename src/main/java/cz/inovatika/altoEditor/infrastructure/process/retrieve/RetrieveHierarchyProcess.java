package cz.inovatika.altoEditor.infrastructure.process.retrieve;

import java.util.LinkedList;
import java.util.Queue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private final BatchService batchService;
    private final KrameriusService krameriusService;
    private final AltoVersionService altoVersionService;
    private final ObjectHierarchyService objectHierarchyService;

    private final Long krameriusUserId;

    public RetrieveHierarchyProcess(
            BatchService batchService,
            KrameriusService krameriusService,
            AltoVersionService altoVersionService,
            ObjectHierarchyService objectHierarchyService,
            Long krameriusUserId,
            Batch batch) {

        super(batch.getId(), batch.getPriority(), batch.getCreatedAt());

        this.batchService = batchService;
        this.krameriusService = krameriusService;
        this.altoVersionService = altoVersionService;
        this.objectHierarchyService = objectHierarchyService;

        this.krameriusUserId = krameriusUserId;
    }

    @Override
    public void run() {
        Batch batch = batchService.getById(batchId);
        String instance = batch.getInstance();

        Queue<KrameriusObjectMetadata> metadataQueue = new LinkedList<>();

        try {
            // --- START PROCESSING ---
            // Do all initializations in this block
            batchService.setState(batch, BatchState.RUNNING);

            String originPid = batch.getPid();
            KrameriusObjectMetadata targetMetadata = krameriusService.getObjectMetadata(originPid, instance);

            if (targetMetadata == null) {
                batchService.setFailed(batch,
                        "Target object with PID " + originPid + " not found in Kramerius instance " + instance
                                + ".");
                return;
            }

            metadataQueue.add(targetMetadata);

            while (!metadataQueue.isEmpty()) {
                KrameriusObjectMetadata currMetadata = metadataQueue.poll();

                // Save Hierarchy info
                DigitalObject targetDigitalObject = objectHierarchyService.store(currMetadata);

                // If PAGE, retrieve ALTO and save AltoVersion
                // Otherwise, fetch children and add them to queue
                if (Model.PAGE.isModel(currMetadata.getModel())) {
                    altoVersionService.updateOrCreateKrameriusVersion(
                        targetDigitalObject.getPid(),
                        this.krameriusUserId,
                        krameriusService.getAltoBytes(currMetadata.getPid(), instance)
                    );
                } else {
                    metadataQueue.addAll(krameriusService.getChildrenMetadata(currMetadata.getPid(), instance));
                }
            }

            // --- FINISH ---
            batchService.setState(batch, BatchState.DONE);

        } catch (Exception ex) {
            LOGGER.error("Batch " + batch.getId() + " failed: " + ex.getMessage(), ex);

            try {
                batchService.setFailed(batch, "Batch " + batch.getId() + " failed: " + ex.getMessage());
            } catch (Exception e) {
                LOGGER.error("Failed to set batch as failed: " + e.getMessage(), e);
            }
        }
    }
}