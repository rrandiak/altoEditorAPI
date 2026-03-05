package cz.inovatika.altoEditor.infrastructure.process.templates;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs an external process in own thread to handle possible process freeze.
 */
public class AsyncProcess extends Thread {

    private static final Logger LOGGER = LoggerFactory.getLogger(AsyncProcess.class);

    private final List<String> cmdLine;
    private final AtomicReference<Process> refProcess = new AtomicReference<>();
    private final AtomicBoolean done = new AtomicBoolean(false);
    private int exitCode = -1;

    private OutputConsumer stdoutConsumer = null;
    private OutputConsumer stderrConsumer = null;

    public AsyncProcess(List<String> cmdLine) {
        this.cmdLine = cmdLine;
    }

    @Override
    public void run() {
        done.set(false);
        exitCode = -1;

        ProcessBuilder pb = new ProcessBuilder(cmdLine);
        pb.redirectErrorStream(true);

        try {
            Process process = pb.start();
            refProcess.set(process);

            stdoutConsumer = new OutputConsumer(process.getInputStream());
            Thread stdoutThread = new Thread(stdoutConsumer, "AsyncProcess-StdOutConsumer");
            stdoutThread.start();

            stderrConsumer = new OutputConsumer(process.getInputStream());
            Thread stderrThread = new Thread(stderrConsumer, "AsyncProcess-StdErrConsumer");
            stderrThread.start();

            exitCode = process.waitFor();
            stdoutThread.join();
            stderrThread.join();

            LOGGER.debug("Process done: {}", cmdLine);
        } catch (Exception ex) {
            LOGGER.error("Failed running process {}", cmdLine, ex);
        } finally {
            closeProcessStreams();
            done.set(true);
        }
    }

    public boolean isDone() {
        return done.get();
    }

    public int getExitCode() {
        return exitCode;
    }

    public String getStdOut() {
        return stdoutConsumer != null ? stdoutConsumer.getOutput() : null;
    }

    public String getStdErr() {
        return stderrConsumer != null ? stderrConsumer.getOutput() : null;
    }

    private void waitForOutputConsumer(OutputConsumer consumer, String name) {
        if (consumer != null) {
            try {
                consumer.join();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                LOGGER.error("Interrupted while waiting for " + name, ex);
            }
        }
    }

    public void kill() {
        String msg = "Kill called. Done: " + isDone() + ", Command: " + cmdLine;
        if (isDone()) {
            LOGGER.debug(msg);
        } else {
            LOGGER.warn(msg);
        }

        Process process = refProcess.getAndSet(null);
        if (process != null) {
            process.destroy();
            closeProcessStreams(process);
            done.set(true);
            waitForOutputConsumer(stderrConsumer, "StdOutConsumer");
            waitForOutputConsumer(stderrConsumer, "StdErrConsumer");
        }
    }

    private void closeProcessStreams() {
        Process process = refProcess.get();
        if (process != null) {
            closeProcessStreams(process);
        }
    }

    private void closeProcessStreams(Process process) {
        try {
            process.getInputStream().close();
        } catch (IOException ignored) {}
        try {
            process.getErrorStream().close();
        } catch (IOException ignored) {}
        try {
            process.getOutputStream().close();
        } catch (IOException ignored) {}
    }
}