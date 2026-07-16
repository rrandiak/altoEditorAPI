package cz.inovatika.altoEditor.infrastructure.process.altoocr;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import cz.inovatika.altoEditor.domain.enums.BatchState;
import cz.inovatika.altoEditor.domain.enums.BatchType;
import cz.inovatika.altoEditor.domain.model.Batch;
import cz.inovatika.altoEditor.domain.enums.HierarchyGenerateScope;
import cz.inovatika.altoEditor.domain.model.dto.HierarchyGenerateInput;
import cz.inovatika.altoEditor.domain.service.AltoVersionService;
import cz.inovatika.altoEditor.domain.service.BatchService;
import cz.inovatika.altoEditor.infrastructure.kramerius.KrameriusService;
import cz.inovatika.altoEditor.infrastructure.process.altoocr.engine.OcrEngine;
import cz.inovatika.altoEditor.infrastructure.process.altoocr.engine.OcrResult;
import cz.inovatika.altoEditor.infrastructure.process.templates.BatchProcess;

public class AltoOcrGeneratorProcess extends BatchProcess {

    private static final Logger LOGGER = LoggerFactory.getLogger(AltoOcrGeneratorProcess.class);

    private final BatchService batchService;
    private final AltoVersionService altoVersionService;
    private final KrameriusService krameriusService;

    private final Long engineUserId;
    private final String engineName;
    private final OcrEngine ocrEngine;
    private final EngineExecutorServiceRegistry executorRegistry;
    private final ObjectMapper objectMapper;

    public AltoOcrGeneratorProcess(
            BatchService batchService,
            AltoVersionService altoVersionService,
            KrameriusService krameriusService,
            Long engineUserId,
            String engineName,
            OcrEngine ocrEngine,
            EngineExecutorServiceRegistry executorRegistry,
            ObjectMapper objectMapper,
            Batch batch) {

        super(batch.getId(), batch.getPriority(), batch.getCreatedAt(), batch.getType());

        this.batchService = batchService;
        this.altoVersionService = altoVersionService;
        this.krameriusService = krameriusService;

        this.engineUserId = engineUserId;
        this.engineName = engineName;
        this.ocrEngine = ocrEngine;
        this.executorRegistry = executorRegistry;
        this.objectMapper = objectMapper;
    }

    private void processPid(Batch batch, String instance, String pid, AtomicInteger processedCount) {
        byte[] imageBytes = krameriusService.getImageBytes(pid, instance);

        OcrResult result = ocrEngine.generate(pid, imageBytes);

        altoVersionService.updateOrCreateEngineVersion(pid, this.engineUserId, result.alto());

        int newTotal = processedCount.incrementAndGet();
        batchService.setProcessedItemCount(batch, newTotal);
    }

    @Override
    public void run() {
        Batch batch = batchService.getById(batchId);
        String instance = batch.getInstance();

        try {
            // --- START PROCESSING ---
            // Do all initializations in this block
            batchService.setState(batch, BatchState.RUNNING);
            batchService.setProcessedItemCount(batch, 0);

            List<String> targetPids;
            if (batch.getType() == BatchType.GENERATE_SINGLE) {
                targetPids = List.of(batch.getPid());
            } else {
                HierarchyGenerateScope scope = HierarchyGenerateScope.ALL;
                if (batch.getData() != null && !batch.getData().isBlank()) {
                    try {
                        HierarchyGenerateInput input = objectMapper.readValue(batch.getData(),
                                HierarchyGenerateInput.class);
                        if (input.getScope() != null) {
                            scope = input.getScope();
                        }
                    } catch (Exception e) {
                        LOGGER.warn("Could not parse batch data for hierarchy scope, using ALL: {}", e.getMessage());
                    }
                }
                targetPids = altoVersionService.distinctPidsByAncestorPid(batch.getPid(), scope, this.engineName);
            }

            batchService.setEstimatedItemCount(batch, targetPids.size());

            LOGGER.info("Starting generation of ALTO and OCR for batch {} with {} PIDs", batchId, targetPids.size());

            // Process PIDs in parallel using engine-scoped pool
            AtomicInteger processedCount = new AtomicInteger(0);
            ExecutorService executor = executorRegistry.getOrCreate(engineName);

            List<Future<?>> futures = new ArrayList<>();
            for (String pid : targetPids) {
                futures.add(executor.submit(() -> processPid(batch, instance, pid, processedCount)));
            }

            // Wait for all PIDs to be processed; if any fails, cancel remaining and fail
            // the batch
            try {
                for (Future<?> f : futures) {
                    f.get();
                }
            } catch (ExecutionException e) {
                for (Future<?> f : futures) {
                    f.cancel(true);
                }
                throw e.getCause() instanceof RuntimeException re ? re : new RuntimeException(e.getCause());
            }

            // --- FINISH ---
            batchService.setState(batch, BatchState.DONE);

            LOGGER.info("Finished generation of ALTO and OCR for batch {} with {} PIDs", batchId, targetPids.size());

        } catch (Exception ex) {
            LOGGER.error("AltoOcrGeneratorProcess batch {} failed: {}", batchId, ex.getMessage(), ex);

            try {
                batchService.setFailed(batch, ex.getMessage());
            } catch (Exception e) {
                LOGGER.error("Failed to set batch as failed: " + e.getMessage(), e);
            }
        }
    }
}