package cz.inovatika.altoEditor.infrastructure.process.accept;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.databind.ObjectMapper;

import cz.inovatika.altoEditor.config.properties.BatchProperties;
import cz.inovatika.altoEditor.domain.adapter.PidAdapter;
import cz.inovatika.altoEditor.domain.enums.BatchState;
import cz.inovatika.altoEditor.domain.model.Batch;
import cz.inovatika.altoEditor.domain.model.dto.AltoVersionSearchFilter;
import cz.inovatika.altoEditor.domain.service.AltoVersionService;
import cz.inovatika.altoEditor.domain.service.BatchService;
import cz.inovatika.altoEditor.domain.service.ObjectHierarchyService;
import cz.inovatika.altoEditor.domain.service.container.AltoVersionUploadContent;
import cz.inovatika.altoEditor.infrastructure.kramerius.KrameriusService;
import cz.inovatika.altoEditor.infrastructure.process.templates.BatchProcess;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AcceptVersionsProcess extends BatchProcess {

    private static final int PROGRESS_EVERY_N_ITEMS = 25;

    private final BatchService batchService;
    private final AltoVersionService altoVersionService;
    private final KrameriusService krameriusService;
    private final ObjectHierarchyService objectHierarchyService;
    private final BatchProperties batchProperties;
    private final ObjectMapper objectMapper;

    public AcceptVersionsProcess(
            BatchService batchService,
            AltoVersionService altoVersionService,
            KrameriusService krameriusService,
            ObjectHierarchyService objectHierarchyService,
            BatchProperties batchProperties,
            ObjectMapper objectMapper,
            Batch batch) {
        super(batch.getId(), batch.getPriority(), batch.getCreatedAt(), batch.getType());

        this.batchService = batchService;
        this.altoVersionService = altoVersionService;
        this.krameriusService = krameriusService;
        this.objectHierarchyService = objectHierarchyService;
        this.batchProperties = batchProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run() {
        Batch batch = batchService.getById(batchId);

        int workers = Math.max(1, batchProperties.getWorkerThreads() - 1);
        ExecutorService pool = Executors.newFixedThreadPool(workers);

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

            // Accept each version in parallel: each task uploads ALTO/OCR to Kramerius
            // then flips the version to ACTIVE. Ancestor page-count stats are refreshed
            // once, after the pool drains (see refreshPageCountsForAcceptedPages).
            AtomicInteger processed = new AtomicInteger(0);
            List<Future<String>> futures = new ArrayList<>();
            for (Integer versionId : versionIds) {
                futures.add(pool.submit(() -> acceptVersion(batch, versionId, processed)));
            }

            List<String> acceptedPids = new ArrayList<>();
            List<UUID> acceptedPageUuids = new ArrayList<>();
            try {
                for (Future<String> future : futures) {
                    String pid = future.get();
                    acceptedPids.add(pid);
                    acceptedPageUuids.add(PidAdapter.toUuid(pid));
                }
            } catch (ExecutionException e) {
                for (Future<String> future : futures) {
                    future.cancel(true);
                }
                throw e.getCause() instanceof RuntimeException re ? re : new RuntimeException(e.getCause());
            }

            batchService.setProcessedItemCount(batch, processed.get());

            objectHierarchyService.refreshPageCountsForAcceptedPages(acceptedPageUuids);

            // Make the accepted ALTO searchable: rebuild the processing index for the
            // accepted pages (rebuild is not recursive), wait for that to finish, then
            // reindex — recursively from the pid the batch was accepted with (its subtree
            // only, e.g. the accepted periodical item, not the whole periodical), else the
            // pages. A rebuild failure or timeout throws and fails the batch (below).
            krameriusService.rebuildAndReindex(acceptedPids, batch.getPid());

            batchService.setState(batch, BatchState.DONE);

        } catch (Exception e) {
            log.error("AcceptVersionsProcess batch {} failed: {}", batchId, e.getMessage(), e);

            try {
                batchService.setFailed(batch, e.getMessage());
            } catch (Exception e2) {
                log.error("Failed to set batch as failed: " + e2.getMessage(), e2);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private String acceptVersion(Batch batch, int versionId, AtomicInteger processed) {
        AltoVersionUploadContent uploadContent = altoVersionService.getAltoVersionUploadContent(versionId);

        krameriusService.uploadAltoOcr(uploadContent.getPid(), uploadContent.getAltoContent(),
                uploadContent.getOcrContent());

        altoVersionService.accept(versionId, false);

        int done = processed.incrementAndGet();
        if (done % PROGRESS_EVERY_N_ITEMS == 0) {
            batchService.setProcessedItemCount(batch, done);
        }

        return uploadContent.getPid();
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
