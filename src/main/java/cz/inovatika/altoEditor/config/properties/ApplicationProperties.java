package cz.inovatika.altoEditor.config.properties;

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import cz.inovatika.altoEditor.domain.enums.BatchType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Validated
@ConfigurationProperties(prefix = "application")
public class ApplicationProperties {
    private String version;
    @NotNull
    private Integer port = 8080;
    /** Global max running batches (used when no per-type limit is set). */
    @NotNull
    private Integer maxProcesses = 5;
    /**
     * Max running batches per process type. Keys: batch type name (e.g. GENERATE_FOR_HIERARCHY).
     * If a type is missing, {@link #maxProcesses} is used.
     */
    private Map<String, Integer> maxProcessesPerType = new HashMap<>();

    /** Max concurrent batches for the given type; falls back to {@link #maxProcesses}. */
    public int getMaxProcessesForType(BatchType type) {
        if (maxProcessesPerType == null) {
            return maxProcesses;
        }
        Integer perType = maxProcessesPerType.get(type.name());
        return perType != null ? perType : maxProcesses;
    }

    @NotNull
    private String workDir = "/tmp/altoEditor";
}