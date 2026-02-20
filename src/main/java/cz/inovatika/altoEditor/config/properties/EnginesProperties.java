package cz.inovatika.altoEditor.config.properties;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Validated
@ConfigurationProperties(prefix = "application")
public class EnginesProperties {

    private Map<String, EngineConfig> engines = new HashMap<>();

    @Data
    @Validated
    public static class EngineConfig {
        @NotBlank
        private String exec;
        @NotBlank
        private String entry;

        private String inImageArg = "--image";
        private String outAltoArg = "--alto";
        private String outOcrArg = "--txt";

        private String apiKey = null;
        private String apiKeyArg = "--key";

        private Boolean batchMode = false;
        private String csvArg = "--csv";

        private List<String> additionalArgs = new ArrayList<>();

        @AssertTrue(message = "When batchMode is true, csvArg must be set; when batchMode is false, inImageArg, outAltoArg and outOcrArg must be set.")
        public boolean isValidBatchModeConfiguration() {
            if (shouldUseBatchMode()) {
                return csvArg != null && !csvArg.isBlank();
            }
            return inImageArg != null && !inImageArg.isBlank()
                    && outAltoArg != null && !outAltoArg.isBlank()
                    && outOcrArg != null && !outOcrArg.isBlank();
        }

        @AssertTrue(message = "When useApiKey is true, apiKeyArg must be set.")
        public boolean isValidApiKeyConfiguration() {
            if (shouldUseApiKey()) {
                return apiKeyArg != null && !apiKeyArg.isBlank();
            }
            return true;
        }

        @NotNull
        private Integer batchSize = 1000;

        @NotNull
        private Long timeout = 180_000L;

        public boolean shouldUseApiKey() {
            return apiKey != null && !apiKey.isBlank();
        }

        public boolean shouldUseBatchMode() {
            return Boolean.TRUE.equals(batchMode);
        }
    }

    public EngineConfig getEngineConfig(String engine) {
        EngineConfig config = engines.get(engine);
        if (config == null) {
            throw new IllegalArgumentException("Engine configuration not found for engine: " + engine);
        }
        return config;
    }
}
