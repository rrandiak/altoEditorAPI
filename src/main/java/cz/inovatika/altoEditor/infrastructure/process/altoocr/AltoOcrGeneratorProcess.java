package cz.inovatika.altoEditor.infrastructure.process.altoocr;

import java.io.File;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cz.inovatika.altoEditor.config.properties.EnginesProperties;
import cz.inovatika.altoEditor.domain.enums.BatchState;
import cz.inovatika.altoEditor.domain.enums.BatchType;
import cz.inovatika.altoEditor.domain.model.Batch;
import cz.inovatika.altoEditor.domain.service.AltoVersionService;
import cz.inovatika.altoEditor.domain.service.BatchService;
import cz.inovatika.altoEditor.infrastructure.kramerius.KrameriusService;
import cz.inovatika.altoEditor.infrastructure.process.templates.BatchProcess;
import cz.inovatika.altoEditor.infrastructure.process.templates.ExternalProcess;
import cz.inovatika.altoEditor.infrastructure.storage.WorkDirectoryService;

public class AltoOcrGeneratorProcess extends BatchProcess {

    private static final Logger LOGGER = LoggerFactory.getLogger(AltoOcrGeneratorProcess.class);

    private final WorkDirectoryService workDirectoryService;

    private final BatchService batchService;
    private final AltoVersionService altoVersionService;
    private final KrameriusService krameriusService;

    private final Long engineUserId;
    private final EnginesProperties.EngineConfig engineConfig;

    public AltoOcrGeneratorProcess(
            WorkDirectoryService workDirectoryService,
            BatchService batchService,
            AltoVersionService altoVersionService,
            KrameriusService krameriusService,
            Long engineUserId,
            EnginesProperties.EngineConfig engineConfig,
            Batch batch) {

        super(batch.getId(), batch.getPriority(), batch.getCreatedAt());

        this.workDirectoryService = workDirectoryService;

        this.batchService = batchService;
        this.altoVersionService = altoVersionService;
        this.krameriusService = krameriusService;

        this.engineUserId = engineUserId;
        this.engineConfig = engineConfig;
    }

    private ExternalProcess createSingleExternalProcess(File workDir, String pid) {
        return new GenerateSingleExternalProcess(engineConfig,
                new File(workDir, pid + ".jpg"),
                new File(workDir, pid + ".xml"),
                new File(workDir, pid + ".txt"));
    }

    private void runExternalProcess(Batch batch, ExternalProcess externalProcess) {
        externalProcess.run();
        if (!externalProcess.isOk()) {
            throw new RuntimeException(
                    "Generating ALTO and OCR for PID " + batch.getPid() + " failed:\n" +
                            "Exit code: " + externalProcess.getExitCode() + "\n" +
                            "Out: " + externalProcess.getOut() + "\n" +
                            "Err: " + externalProcess.getErr());
        }
    }

    private void processPid(Batch batch, String instance, String pid, AtomicInteger processedCount) {
        File workDir = workDirectoryService.createWorkDir("batch-" + batch.getId() + "-");
        try {
            try {
                workDirectoryService.saveBytesToFile(
                        workDir,
                        pid + ".jpg",
                        krameriusService.getImageBytes(pid, instance));
            } catch (java.io.IOException e) {
                throw new UncheckedIOException(e);
            }

            runExternalProcess(batch, createSingleExternalProcess(workDir, pid));

            try {
                altoVersionService.updateOrCreateEngineVersion(
                        pid,
                        this.engineUserId,
                        Files.readAllBytes(new File(workDir, pid + ".xml").toPath()));
            } catch (java.io.IOException e) {
                throw new UncheckedIOException(e);
            }

            int newTotal = processedCount.incrementAndGet();
            batchService.setProcessedItemCount(batch, newTotal);
        } finally {
            workDirectoryService.cleanup(workDir);
        }
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

            List<String> targetPids = batch.getType() == BatchType.GENERATE_SINGLE
                    ? List.of(batch.getPid())
                    : altoVersionService.distinctPidsByAncestorPid(batch.getPid());

            batchService.setEstimatedItemCount(batch, targetPids.size());

            LOGGER.info("Starting generation of ALTO and OCR for batch {} with {} PIDs", batchId, targetPids.size());

            // Process PIDs in parallel
            AtomicInteger processedCount = new AtomicInteger(0);
            int parallelism = Math.min(engineConfig.getParallelism(), Math.max(1, targetPids.size()));
            ExecutorService executor = Executors.newFixedThreadPool(parallelism);

            List<Future<?>> futures = new ArrayList<>();
            for (String pid : targetPids) {
                futures.add(executor.submit(() -> processPid(batch, instance, pid, processedCount)));
            }

            // Wait for all PIDs to be processed
            try {
                for (Future<?> f : futures) {
                    f.get();
                }
            } catch (ExecutionException e) {
                executor.shutdownNow();
                throw e.getCause() instanceof RuntimeException re ? re : new RuntimeException(e.getCause());
            } finally {
                executor.shutdown();
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