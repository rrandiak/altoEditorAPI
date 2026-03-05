
package cz.inovatika.altoEditor.infrastructure.process.templates;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The helper to run external processes.
 */
public class ExternalProcess implements Runnable {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExternalProcess.class);
    public static final long DEFAULT_TIMEOUT = 2 * 60 * 1000;
    public static final int DEFAULT_RETRY_ATTEMPTS = 3;

    private AsyncProcess asyncProcess;

    protected ExternalProcess() {
    }

    @Override
    public void run() {
        List<String> cmdLine = buildCmdLine();
        try {
            int numberOfAttemps = getRetryAttempts();
            for (int i = 0; i < numberOfAttemps; i++) {
                runCmdLine(cmdLine);
                if (isOk()) {
                    return;
                }
                LOGGER.warn("Cmd: {}\nAttempt {} failure:\n{}",
                        new Object[] { cmdLine, i + 1, getFullOutput() });
            }
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        } catch (InterruptedException ex) {
            throw new IllegalStateException(ex);
        }
    }

    protected List<String> buildCmdLine() {
        List<String> cmdLine = new ArrayList<>();
        return cmdLine;
    }

    private int runCmdLine(List<String> cmdLine) throws IOException, InterruptedException {
        StringBuilder debug = new StringBuilder();
        for (String arg : cmdLine) {
            debug.append(arg).append(" ");
        }
        LOGGER.debug("run: " + debug);
        asyncProcess = new AsyncProcess(cmdLine);
        asyncProcess.start();
        long timeout = getTimeout();
        asyncProcess.join(timeout);
        asyncProcess.kill();
        LOGGER.debug(getFullOutput());
        return asyncProcess.getExitCode();
    }

    public String getStdOut() {
        return asyncProcess == null ? null : asyncProcess.getStdOut();
    }

    public String getStdErr() {
        return asyncProcess == null ? null : asyncProcess.getStdOut();
    }

    public Integer getExitCode() {
        return asyncProcess == null ? null : asyncProcess.getExitCode();
    }

    public boolean isOk() {
        return getExitCode() == 0;
    }

    public String getFullOutput() {
        StringJoiner sj = new StringJoiner("\n");

        sj.add("Exit code: " + this.getExitCode());
        sj.add("StdOut: " + this.getStdOut());
        sj.add("StdErr: " + this.getStdErr());

        return sj.toString();
    }

    public long getTimeout() {
        return DEFAULT_TIMEOUT;
    }

    public int getRetryAttempts() {
        return DEFAULT_RETRY_ATTEMPTS;
    }
}
