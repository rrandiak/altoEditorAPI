package cz.inovatika.altoEditor.infrastructure.process.altoocr;

import cz.inovatika.altoEditor.config.properties.EnginesProperties;
import cz.inovatika.altoEditor.domain.enums.BatchState;
import cz.inovatika.altoEditor.domain.enums.BatchSubstate;
import cz.inovatika.altoEditor.domain.model.Batch;
import cz.inovatika.altoEditor.domain.model.AltoVersion;
import cz.inovatika.altoEditor.domain.repository.AltoVersionRepository;
import cz.inovatika.altoEditor.domain.service.BatchService;
import cz.inovatika.altoEditor.infrastructure.kramerius.KrameriusService;
import cz.inovatika.altoEditor.infrastructure.process.templates.BatchProcess;
import cz.inovatika.altoEditor.infrastructure.storage.AkubraService;
import cz.inovatika.altoEditor.infrastructure.storage.WorkDirectoryService;
import cz.inovatika.altoEditor.presentation.security.UserProfile;

import java.io.File;
import java.nio.file.Files;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AltoOcrGeneratorProcess extends BatchProcess {

    private static final Logger LOGGER = LoggerFactory.getLogger(AltoOcrGeneratorProcess.class);

    private final WorkDirectoryService workDirectoryService;

    private final BatchService batchService;
    private final AltoVersionRepository digitalObjectRepository;
    private final AkubraService akubraService;
    private final KrameriusService krameriusService;

    private final UserProfile userProfile;
    private final EnginesProperties.EngineConfig generatorConfig;

    public AltoOcrGeneratorProcess(
            WorkDirectoryService workDirectoryService,
            BatchService batchService,
            AltoVersionRepository digitalObjectRepository,
            AkubraService akubraService,
            KrameriusService krameriusService,
            Batch batch,
            UserProfile userProfile,
            EnginesProperties.EngineConfig generatorConfig) {

        super(batch.getId(), batch.getPriority(), batch.getCreatedAt());

        this.workDirectoryService = workDirectoryService;
        this.batchService = batchService;
        this.digitalObjectRepository = digitalObjectRepository;
        this.akubraService = akubraService;
        this.krameriusService = krameriusService;
        this.userProfile = userProfile;
        this.generatorConfig = generatorConfig;
    }

    @Override
    public void run() {
        Batch batch = batchService.getById(batchId);
        File workDir = null;

        try {
            // --- START PROCESSING ---
            // Do all initializations in this block
            batchService.setState(batch, BatchState.RUNNING);

            workDir = workDirectoryService.createWorkDir("batch-" + batch.getId() + "-");

            Optional<AltoVersion> objOpt = digitalObjectRepository
                    .findFirstByDigitalObjectUuidAndInstance(batch.getUuid(), batch.getInstance()).stream().findFirst();

            if (objOpt.isEmpty()) {
                batchService.setFailed(batch,
                        "Digital object with PID " + batch.getPid() + " and instance " + batch.getInstance()
                                + " not found.");
                return;
            }
            AltoVersion obj = objOpt.get();

            batchService.setEstimatedItemCount(batch, 1);

            // --- DOWNLOAD IMAGES ---
            // Download images from Kramerius and save them to workDir
            batchService.setSubstate(batch, BatchSubstate.DOWNLOADING);

            workDirectoryService.saveBytesToFile(workDir, "image.jpg",
                    krameriusService.getImageBytes(obj.getDigitalObject().getPid(), batch.getInstance(), userProfile.getToken()));

            // --- GENERATE ALTO/OCR ---
            // Run selected engine to generate ALTO and OCR from downloaded images
            // and save the results to workDir
            batchService.setSubstate(batch, BatchSubstate.GENERATING);

            AltoOcrExternalProcess externalProcess = new AltoOcrExternalProcess(generatorConfig,
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
                    obj.getDigitalObject().getPid(),
                    obj.getVersion(),
                    Files.readAllBytes(new File(workDir, "output.xml").toPath()));

            akubraService.saveOcrContent(
                    obj.getDigitalObject().getPid(),
                    obj.getVersion(),
                    Files.readAllBytes(new File(workDir, "output.txt").toPath()));

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