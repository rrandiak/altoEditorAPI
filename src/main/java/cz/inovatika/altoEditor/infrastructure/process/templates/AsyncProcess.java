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
    private OutputConsumer outputConsumer;

    public AsyncProcess(List<String> cmdLine) {
        this.cmdLine = cmdLine;
    }

    @Override
    public void run() {
        done.set(false);
        exitCode = -1;
        outputConsumer = null;

        ProcessBuilder pb = new ProcessBuilder(cmdLine);
        pb.redirectErrorStream(true);

        try {
            Process process = pb.start();
            refProcess.set(process);

            outputConsumer = new OutputConsumer(process.getInputStream());
            Thread outputThread = new Thread(outputConsumer, "AsyncProcess-OutputConsumer");
            outputThread.start();

            exitCode = process.waitFor();
            outputThread.join();

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

    public String getOut() {
        return outputConsumer != null ? outputConsumer.getOutput() : "";
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
            if (outputConsumer != null) {
                try {
                    outputConsumer.join();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    LOGGER.error("Interrupted while waiting for OutputConsumer", ex);
                }
            }
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