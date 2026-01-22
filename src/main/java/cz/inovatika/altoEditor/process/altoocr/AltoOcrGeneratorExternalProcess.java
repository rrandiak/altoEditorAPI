package cz.inovatika.altoEditor.process.altoocr;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import cz.inovatika.altoEditor.config.ProcessorsConfig;
import cz.inovatika.altoEditor.process.templates.ExternalProcess;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class AltoOcrGeneratorExternalProcess extends ExternalProcess {

    private final ProcessorsConfig.ProcessorConfig onfig;
    private final File inImageFile;
    private final File outAltoFile;
    private final File outOcrFile;

    @Override
    public void run() {
        if (!inImageFile.exists()) {
            throw new IllegalStateException(inImageFile.getAbsolutePath() + " not exists!");
        }
        if (outAltoFile.exists()) {
            throw new IllegalStateException(outAltoFile.getAbsolutePath() + " exists!");
        }
        if (outOcrFile.exists()) {
            throw new IllegalStateException(outOcrFile.getAbsolutePath() + " exists!");
        }
        super.run();
    }

    @Override
    protected List<String> buildCmdLine() {
        List<String> cmdLine = new ArrayList<>();
        cmdLine.add(onfig.getExec());
        cmdLine.add(onfig.getEntry());

        cmdLine.add(onfig.getInImageArg());
        cmdLine.add(inImageFile.getAbsolutePath());

        cmdLine.add(onfig.getOutAltoArg());
        cmdLine.add(outAltoFile.getAbsolutePath());

        cmdLine.add(onfig.getOutOcrArg());
        cmdLine.add(outOcrFile.getAbsolutePath());

        for (String arg : onfig.getAdditionalArgs()) {
            cmdLine.add(arg);
        }

        return cmdLine;
    }

    @Override
    public long getTimeout() {
        return onfig.getTimeout();
    }

    @Override
    public boolean isOk() {
        return outOcrFile.exists() &&
                outAltoFile.exists() && outAltoFile.length() > 0;
    }
}
