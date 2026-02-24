package cz.inovatika.altoEditor.infrastructure.process.accept;

import java.util.List;

import cz.inovatika.altoEditor.domain.adapter.PidAdapter;
import cz.inovatika.altoEditor.domain.enums.BatchState;
import cz.inovatika.altoEditor.domain.model.Batch;
import cz.inovatika.altoEditor.domain.repository.AltoVersionRepository;
import cz.inovatika.altoEditor.domain.service.AltoVersionService;
import cz.inovatika.altoEditor.domain.service.BatchService;
import cz.inovatika.altoEditor.infrastructure.process.templates.BatchProcess;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AcceptEngineVersionsProcess extends BatchProcess {

    private final BatchService batchService;

    private final AltoVersionService altoVersionService;

    private final AltoVersionRepository altoVersionRepository;

    public AcceptEngineVersionsProcess(BatchService batchService, AltoVersionService altoVersionService,
            AltoVersionRepository altoVersionRepository, Batch batch) {
        super(batch.getId(), batch.getPriority(), batch.getCreatedAt());

        this.batchService = batchService;
        this.altoVersionService = altoVersionService;

        this.altoVersionRepository = altoVersionRepository;
    }

    @Override
    public void run() {
        Batch batch = batchService.getById(batchId);

        try {
            // --- START ---
            batchService.setState(batch, BatchState.RUNNING);
            batchService.setProcessedItemCount(batch, 0);

            List<Integer> versionIds = altoVersionRepository.findPendingVersionIdsByUserInHierarchy(
                    PidAdapter.toUuid(batch.getPid()), batch.getCreatedBy().getId());

            batchService.setEstimatedItemCount(batch, versionIds.size());

            // --- ACCEPT VERSIONS ---
            for (Integer versionId : versionIds) {
                altoVersionService.accept(versionId);

                batchService.setProcessedItemCount(batch, batch.getProcessedItemCount() + 1);
            }

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
