package cz.inovatika.altoEditor.storage;

import cz.inovatika.altoEditor.config.ApplicationConfig;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
@RequiredArgsConstructor
public class WorkDirectoryService {

    private static final Logger LOGGER = LoggerFactory.getLogger(WorkDirectoryService.class);

    private final ApplicationConfig applicationConfig;

    /**
     * Creates a temporary work directory for a batch
     */
    public File createWorkDir(String prefix) {
        File appWorkDir = new File(applicationConfig.getWorkDir());
        if (!appWorkDir.exists() && !appWorkDir.mkdirs()) {
            throw new IllegalStateException("Failed to create workDir: " + appWorkDir.getAbsolutePath());
        }

        try {
            Path tempDir = Files.createTempDirectory(appWorkDir.toPath(), prefix);
            File workDir = tempDir.toFile();
            LOGGER.info("Created work directory: " + workDir.getAbsolutePath());
            return workDir;
        } catch (IOException e) {
            throw new RuntimeException("Failed to create temporary work directory", e);
        }
    }

    /**
     * Saves bytes to a file in the specified directory
     */
    public void saveBytesToFile(File directory, String filename, byte[] bytes) throws IOException {
        Files.write(new File(directory, filename).toPath(), bytes);
    }

    /**
     * Reads bytes from a file
     */
    public byte[] readBytesFromFile(File directory, String filename) throws IOException {
        return Files.readAllBytes(new File(directory, filename).toPath());
    }

    /**
     * Cleans up (deletes) a work directory
     */
    public void cleanup(File workDir) {
        if (workDir != null && workDir.exists()) {
            try {
                deleteDirectory(workDir);
                LOGGER.info("Cleaned up work directory: " + workDir.getAbsolutePath());
            } catch (IOException e) {
                LOGGER.warn("Failed to cleanup work directory: " + workDir.getAbsolutePath(), e);
            }
        }
    }

    /**
     * Recursively deletes a directory and all its contents
     */
    private void deleteDirectory(File directory) throws IOException {
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteDirectory(file);
                } else {
                    if (!file.delete()) {
                        throw new IOException("Failed to delete file: " + file.getAbsolutePath());
                    }
                }
            }
        }
        if (!directory.delete()) {
            throw new IOException("Failed to delete directory: " + directory.getAbsolutePath());
        }
    }
}