package cz.inovatika.altoEditor.infrastructure.process.altoocr;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import cz.inovatika.altoEditor.config.properties.EnginesProperties;
import cz.inovatika.altoEditor.infrastructure.process.templates.ExternalProcess;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
public class GenerateBatchExternalProcess extends ExternalProcess {

    private final EnginesProperties.EngineConfig config;
    private final File dataTripletsFile;
    private final List<DataTriplet> dataTriplets;

    @Override
    protected List<String> buildCmdLine() {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(dataTripletsFile))) {
            for (DataTriplet dataTriplet : dataTriplets) {
                writer.write(dataTriplet.toString());
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write data triplets to file: " + dataTripletsFile.getAbsolutePath(), e);
        }

        List<String> cmdLine = new ArrayList<>();

        cmdLine.add(config.getExec());
        cmdLine.add(config.getEntry());

        cmdLine.add(config.getDataTripletsArg());
        cmdLine.add(dataTripletsFile.getAbsolutePath());

        for (String arg : config.getAdditionalArgs()) {
            cmdLine.add(arg);
        }

        return cmdLine;
    }

    @Override
    public long getTimeout() {
        return config.getTimeout();
    }

    @Override
    public boolean isOk() {
        return dataTriplets.stream().allMatch(
            DataTriplet::isOk
        );
    }
    
    @AllArgsConstructor
    @Getter
    static class DataTriplet {
        private final File inImageFile;
        private final File outAltoFile;
        private final File outOcrFile;

        // Ocr can be empty if no text is found in the image,
        // but Alto would always contain some xml structure.
        public boolean isOk() {
            return outOcrFile.exists() &&
                    outAltoFile.exists() && outAltoFile.length() > 0;
        }

        @Override
        public String toString() {
            return inImageFile.getAbsolutePath() + "," + outAltoFile.getAbsolutePath() + "," + outOcrFile.getAbsolutePath();
        }
    }
}
