package cz.inovatika.altoEditor.infrastructure.process.altoocr;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import cz.inovatika.altoEditor.config.properties.EnginesProperties;
import cz.inovatika.altoEditor.infrastructure.process.templates.ExternalProcess;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class GenerateSingleExternalProcess extends ExternalProcess {

    private final EnginesProperties.EngineConfig config;
    private final File inImageFile;
    private final File outAltoFile;
    private final File outOcrFile;

    @Override
    protected List<String> buildCmdLine() {
        List<String> cmdLine = new ArrayList<>();

        cmdLine.add(config.getExec());
        cmdLine.add(config.getEntry());

        cmdLine.add(config.getInImageArg());
        cmdLine.add(inImageFile.getAbsolutePath());

        cmdLine.add(config.getOutAltoArg());
        cmdLine.add(outAltoFile.getAbsolutePath());

        cmdLine.add(config.getOutOcrArg());
        cmdLine.add(outOcrFile.getAbsolutePath());

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
        return outOcrFile.exists() &&
                outAltoFile.exists() && outAltoFile.length() > 0;
    }
}
