package cz.inovatika.altoEditor.infrastructure.process.altoocr.engine;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;

import cz.inovatika.altoEditor.config.properties.EnginesProperties.EngineConfig;
import cz.inovatika.altoEditor.infrastructure.process.altoocr.GenerateSingleExternalProcess;
import cz.inovatika.altoEditor.infrastructure.storage.WorkDirectoryService;

/**
 * OCR engine backed by the legacy external subprocess (e.g. the pero-* Python clients):
 * writes the image to a per-call work dir, runs {@link GenerateSingleExternalProcess}
 * (which produces the ALTO XML and OCR text), reads them back, and cleans up.
 */
public class ExternalProcessOcrEngine implements OcrEngine {

    private final EngineConfig config;
    private final WorkDirectoryService workDirectoryService;

    public ExternalProcessOcrEngine(EngineConfig config, WorkDirectoryService workDirectoryService) {
        this.config = config;
        this.workDirectoryService = workDirectoryService;
    }

    @Override
    public OcrResult generate(String pid, byte[] imageBytes) {
        File workDir = workDirectoryService.createWorkDir("ocr-" + pid + "-");
        try {
            File imageFile = new File(workDir, pid + ".jpg");
            File altoFile = new File(workDir, pid + ".xml");
            File ocrFile = new File(workDir, pid + ".txt");

            try {
                workDirectoryService.saveBytesToFile(workDir, pid + ".jpg", imageBytes);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }

            GenerateSingleExternalProcess process =
                    new GenerateSingleExternalProcess(config, imageFile, altoFile, ocrFile);
            process.run();
            if (!process.isOk()) {
                throw new RuntimeException("Generating ALTO and OCR for PID " + pid + " failed:\n"
                        + process.getFullOutput());
            }

            try {
                byte[] alto = Files.readAllBytes(altoFile.toPath());
                byte[] ocr = ocrFile.exists() ? Files.readAllBytes(ocrFile.toPath()) : null;
                return new OcrResult(alto, ocr);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        } finally {
            workDirectoryService.cleanup(workDir);
        }
    }
}
