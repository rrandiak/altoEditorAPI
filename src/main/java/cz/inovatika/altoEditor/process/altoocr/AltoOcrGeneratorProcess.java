package cz.inovatika.altoEditor.process.altoocr;

import cz.inovatika.altoEditor.api.auth.UserProfile;
import cz.inovatika.altoEditor.config.ProcessorsConfig;
import cz.inovatika.altoEditor.core.entity.Batch;
import cz.inovatika.altoEditor.core.entity.DigitalObject;
import cz.inovatika.altoEditor.core.enums.BatchState;
import cz.inovatika.altoEditor.core.enums.BatchSubstate;
import cz.inovatika.altoEditor.core.enums.BatchType;
import cz.inovatika.altoEditor.core.repository.DigitalObjectRepository;
import cz.inovatika.altoEditor.core.service.BatchService;
import cz.inovatika.altoEditor.kramerius.KrameriusService;
import cz.inovatika.altoEditor.storage.AkubraService;
import cz.inovatika.altoEditor.storage.WorkDirectoryService;
import lombok.AllArgsConstructor;

import java.io.File;
import java.nio.file.Files;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@AllArgsConstructor
public class AltoOcrGeneratorProcess implements Runnable {

    private static final Logger LOGGER = LoggerFactory.getLogger(AltoOcrGeneratorProcess.class);

    private final WorkDirectoryService workDirectoryService;

    private final BatchService batchService;
    private final DigitalObjectRepository digitalObjectRepository;
    private final AkubraService akubraService;
    private final KrameriusService krameriusService;

    private final Batch batch;
    private final UserProfile userProfile;
    private final ProcessorsConfig.ProcessorConfig generatorConfig;

    @Override
    public void run() {
        File workDir = null;

        try {
            // --- START PROCESSING ---
            // Do all initializations in this block
            batchService.setState(batch, BatchState.RUNNING);

            workDir = workDirectoryService.createWorkDir("batch-" + batch.getId() + "-");

            Optional<DigitalObject> objOpt = digitalObjectRepository
                    .findByPidAndInstanceId(batch.getPid(), batch.getInstance()).stream().findFirst();

            if (objOpt.isEmpty()) {
                batchService.setFailed(batch,
                        "Digital object with PID " + batch.getPid() + " and instance " + batch.getInstance()
                                + " not found.");
                return;
            }
            DigitalObject obj = objOpt.get();

            // --- DOWNLOAD IMAGES ---
            // Download images from Kramerius and save them to workDir
            batchService.setSubstate(batch, BatchSubstate.DOWNLOADING);

            workDirectoryService.saveBytesToFile(workDir, "image.jpg",
                    krameriusService.getImageBytes(obj.getPid(), batch.getInstance(), userProfile.getToken()));
            batchService.setRunInfo(batch, 1, BatchType.SINGLE);

            // --- GENERATE ALTO/OCR ---
            // Run selected engine to generate ALTO and OCR from downloaded images
            // and save the results to workDir
            batchService.setSubstate(batch, BatchSubstate.GENERATING);

            AltoOcrGeneratorExternalProcess externalProcess = new AltoOcrGeneratorExternalProcess(generatorConfig,
                    new File(workDir, "image.jpg"),
                    new File(workDir, "output.xml"),
                    new File(workDir, "output.txt"));

            externalProcess.run();
            if (!externalProcess.isOk()) {
                batchService.setFailed(batch,
                        "Generating ALTO and OCR for PID " + batch.getPid() + " failed: " + externalProcess.getErr());
                return;
            }

            // --- SAVE RESULTS ---
            // Save generated ALTO and OCR back to Akubra
            batchService.setSubstate(batch, BatchSubstate.SAVING);

            akubraService.saveAltoContent(
                    obj.getPid(),
                    obj.getVersion(),
                    Files.readAllBytes(new File(workDir, "output.xml").toPath()));

            akubraService.saveOcrContent(
                    obj.getPid(),
                    obj.getVersion(),
                    Files.readAllBytes(new File(workDir, "output.txt").toPath()));

            // --- FINISH ---
            batchService.setState(batch, BatchState.DONE);

        } catch (Exception ex) {
            LOGGER.error("Batch " + this.batch.getId() + " failed: " + ex.getMessage(), ex);

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