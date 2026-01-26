package cz.inovatika.altoEditor.infrastructure.process.templates;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * Reads the external process output in separate thread.
 */
public class OutputConsumer extends Thread {

    private final InputStream input;
    private final StringBuilder output;
    private Throwable error;

    public OutputConsumer(InputStream input) {
        this.input = input;
        output = new StringBuilder();
    }

    @Override
    public void run() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input))) {
            for (String line; (line = reader.readLine()) != null;) {
                if (output.length() > 0) {
                    output.append('\n');
                }
                output.append(line);
            }
        } catch (Throwable ex) {
            error = ex;
        }
    }

    public String getOutput() {
        return output.toString();
    }

    public Throwable getError() {
        return error;
    }
}