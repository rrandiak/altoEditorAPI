package cz.inovatika.altoEditor.infrastructure.process.accept;

import java.util.List;

import cz.inovatika.altoEditor.domain.adapter.PidAdapter;
import cz.inovatika.altoEditor.domain.enums.AltoVersionState;
import cz.inovatika.altoEditor.domain.enums.BatchState;
import cz.inovatika.altoEditor.domain.model.Batch;
import cz.inovatika.altoEditor.domain.model.User;
import cz.inovatika.altoEditor.domain.repository.AltoVersionRepository;
import cz.inovatika.altoEditor.domain.service.AltoVersionService;
import cz.inovatika.altoEditor.domain.service.BatchService;
import cz.inovatika.altoEditor.domain.service.UserService;
import cz.inovatika.altoEditor.domain.service.container.AltoVersionUploadContent;
import cz.inovatika.altoEditor.infrastructure.kramerius.KrameriusService;
import cz.inovatika.altoEditor.infrastructure.process.templates.BatchProcess;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AcceptEngineVersionsProcess extends BatchProcess {

    private final BatchService batchService;

    private final AltoVersionService altoVersionService;

    private final AltoVersionRepository altoVersionRepository;

    private final UserService userService;

    private final KrameriusService krameriusService;

    public AcceptEngineVersionsProcess(
            BatchService batchService,
            AltoVersionService altoVersionService,
            AltoVersionRepository altoVersionRepository,
            UserService userService,
            KrameriusService krameriusService,
            Batch batch) {
        super(batch.getId(), batch.getPriority(), batch.getCreatedAt(), batch.getType());

        this.batchService = batchService;
        this.altoVersionService = altoVersionService;
        this.userService = userService;
        this.krameriusService = krameriusService;

        this.altoVersionRepository = altoVersionRepository;
    }

    @Override
    public void run() {
        Batch batch = batchService.getById(batchId);

        try {
            // --- START ---
            batchService.setState(batch, BatchState.RUNNING);
            batchService.setProcessedItemCount(batch, 0);

            User user = userService.getUserByUsername(batch.getEngine());

            List<Integer> versionIds = altoVersionRepository.findPendingVersionIdsByUserInHierarchy(
                    PidAdapter.toUuid(batch.getPid()), user.getId(),
                    AltoVersionState.PENDING.ordinal());

            batchService.setEstimatedItemCount(batch, versionIds.size());

            // --- ACCEPT VERSIONS ---
            for (Integer versionId : versionIds) {
                AltoVersionUploadContent uploadContent = altoVersionService.getAltoVersionUploadContent(versionId);

                krameriusService.uploadAltoOcr(uploadContent.getPid(), uploadContent.getAltoContent(),
                        uploadContent.getOcrContent());

                altoVersionService.accept(versionId);

                batchService.setProcessedItemCount(batch, batch.getProcessedItemCount() + 1);
            }

            krameriusService.planHierarchyIndexing(batch.getPid());

            // --- FINISH ---
            batchService.setState(batch, BatchState.DONE);

        } catch (Exception e) {
            log.error("AcceptEngineVersionsProcess batch {} failed: {}", batchId, e.getMessage(), e);

            try {
                batchService.setFailed(batch, e.getMessage());
            } catch (Exception e2) {
                log.error("Failed to set batch as failed: " + e2.getMessage(), e2);
            }
        }
    }
}
