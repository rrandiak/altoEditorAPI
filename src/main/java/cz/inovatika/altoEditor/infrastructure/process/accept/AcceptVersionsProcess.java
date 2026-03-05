package cz.inovatika.altoEditor.infrastructure.process.accept;

import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;

import cz.inovatika.altoEditor.domain.enums.BatchState;
import cz.inovatika.altoEditor.domain.model.Batch;
import cz.inovatika.altoEditor.domain.model.dto.AltoVersionSearchFilter;
import cz.inovatika.altoEditor.domain.service.AltoVersionService;
import cz.inovatika.altoEditor.domain.service.BatchService;
import cz.inovatika.altoEditor.domain.service.container.AltoVersionUploadContent;
import cz.inovatika.altoEditor.infrastructure.kramerius.KrameriusService;
import cz.inovatika.altoEditor.infrastructure.process.templates.BatchProcess;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AcceptVersionsProcess extends BatchProcess {

    private final BatchService batchService;
    private final AltoVersionService altoVersionService;
    private final KrameriusService krameriusService;
    private final ObjectMapper objectMapper;

    public AcceptVersionsProcess(
            BatchService batchService,
            AltoVersionService altoVersionService,
            KrameriusService krameriusService,
            ObjectMapper objectMapper,
            Batch batch) {
        super(batch.getId(), batch.getPriority(), batch.getCreatedAt(), batch.getType());

        this.batchService = batchService;
        this.altoVersionService = altoVersionService;
        this.krameriusService = krameriusService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run() {
        Batch batch = batchService.getById(batchId);

        try {
            batchService.setState(batch, BatchState.RUNNING);
            batchService.setProcessedItemCount(batch, 0);

            AltoVersionSearchFilter input = parseInput(batch.getData());

            if (input.getTargetPid() != null) {
                batchService.setPid(batch, input.getTargetPid());
            } else if (input.getHierarchyPid() != null) {
                batchService.setPid(batch, input.getHierarchyPid());
            }

            List<Integer> versionIds = altoVersionService.findVersionIdsByFilter(input);

            batchService.setEstimatedItemCount(batch, versionIds.size());

            for (Integer versionId : versionIds) {
                AltoVersionUploadContent uploadContent = altoVersionService.getAltoVersionUploadContent(versionId);

                krameriusService.uploadAltoOcr(uploadContent.getPid(), uploadContent.getAltoContent(),
                        uploadContent.getOcrContent());

                altoVersionService.accept(versionId);

                batchService.setProcessedItemCount(batch, batch.getProcessedItemCount() + 1);
            }

            batchService.setState(batch, BatchState.DONE);

        } catch (Exception e) {
            log.error("AcceptVersionsProcess batch {} failed: {}", batchId, e.getMessage(), e);

            try {
                batchService.setFailed(batch, e.getMessage());
            } catch (Exception e2) {
                log.error("Failed to set batch as failed: " + e2.getMessage(), e2);
            }
        }
    }

    private AltoVersionSearchFilter parseInput(String data) {
        if (data == null || data.isBlank()) {
            return AltoVersionSearchFilter.builder().build();
        }
        try {
            AltoVersionSearchFilter input = objectMapper.readValue(data, AltoVersionSearchFilter.class);
            return input != null ? input : AltoVersionSearchFilter.builder().build();
        } catch (Exception e) {
            log.warn("Could not parse batch data for accept versions, using empty filter: {}", e.getMessage());
            return AltoVersionSearchFilter.builder().build();
        }
    }
}
