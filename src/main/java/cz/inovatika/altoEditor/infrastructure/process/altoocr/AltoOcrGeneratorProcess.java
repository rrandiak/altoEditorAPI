package cz.inovatika.altoEditor.infrastructure.process.altoocr;

import java.io.File;
import java.nio.file.Files;
import java.util.Iterator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cz.inovatika.altoEditor.config.properties.EnginesProperties;
import cz.inovatika.altoEditor.domain.enums.BatchState;
import cz.inovatika.altoEditor.domain.enums.BatchSubstate;
import cz.inovatika.altoEditor.domain.enums.BatchType;
import cz.inovatika.altoEditor.domain.model.Batch;
import cz.inovatika.altoEditor.domain.service.AltoVersionService;
import cz.inovatika.altoEditor.domain.service.BatchService;
import cz.inovatika.altoEditor.infrastructure.kramerius.KrameriusService;
import cz.inovatika.altoEditor.infrastructure.process.templates.BatchProcess;
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

    private Iterable<List<String>> partitionList(List<String> list, int size) {
        return () -> new Iterator<List<String>>() {
            private int currentIndex = 0;

            @Override
            public boolean hasNext() {
                return currentIndex < list.size();
            }

            @Override
            public List<String> next() {
                int endIndex = Math.min(currentIndex + size, list.size());
                List<String> sublist = list.subList(currentIndex, endIndex);
                currentIndex = endIndex;
                return sublist;
            }
        };
    }

    @Override
    public void run() {
        Batch batch = batchService.getById(batchId);
        String instance = batch.getInstance();
        File workDir = null;

        try {
            // --- START PROCESSING ---
            // Do all initializations in this block
            batchService.setState(batch, BatchState.RUNNING);

            workDir = workDirectoryService.createWorkDir("batch-" + batch.getId() + "-");

            List<String> targetPids = batch.getType() == BatchType.GENERATE_SINGLE
                    ? List.of(batch.getPid())
                    : altoVersionService.distinctPidsByAncestorPid(batch.getPid());

            batchService.setEstimatedItemCount(batch, targetPids.size());

            for (List<String> pidChunk : partitionList(targetPids, engineConfig.getBatchSize())) {
                // --- DOWNLOAD IMAGES ---
                // Download images from Kramerius and save them to workDir
                batchService.setSubstate(batch, BatchSubstate.DOWNLOADING);

                for (String pid : pidChunk) {
                    workDirectoryService.saveBytesToFile(
                            workDir,
                            pid + ".jpg",
                            krameriusService.getImageBytes(pid, instance));
                }

                // --- GENERATE ALTO ---
                // Run selected engine to generate ALTO and OCR from downloaded images
                // and save the results to workDir
                batchService.setSubstate(batch, BatchSubstate.GENERATING);

                for (String pid : pidChunk) {
                    AltoOcrExternalProcess externalProcess = new AltoOcrExternalProcess(engineConfig,
                            new File(workDir, pid + ".jpg"),
                            new File(workDir, pid + ".xml"),
                            new File(workDir, pid + ".txt"));

                    externalProcess.run();
                    if (!externalProcess.isOk()) {
                        batchService.setFailed(batch,
                                "Generating ALTO and OCR for PID " + batch.getPid() + " failed: "
                                        + externalProcess.getErr());
                        return;
                    }
                }

                // --- SAVE RESULTS ---
                // Save generated ALTO (and OCR ?) back to Akubra
                batchService.setSubstate(batch, BatchSubstate.SAVING);

                for (String pid : pidChunk) {
                    altoVersionService.updateOrCreateEngineVersion(
                            pid,
                            this.engineUserId,
                            Files.readAllBytes(new File(workDir, pid + ".xml").toPath()));
                }

                // --- UPDATE PROGRESS ---
                batchService.setProcessedItemCount(batch, batch.getProcessedItemCount() + pidChunk.size());

                // --- CLEANUP WORKDIR ---
                workDirectoryService.cleanup(workDir);
                workDir = workDirectoryService.createWorkDir("batch-" + batch.getId() + "-");
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

        } finally {
            workDirectoryService.cleanup(workDir);
        }
    }
}