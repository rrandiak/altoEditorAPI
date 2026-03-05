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
import jakarta.validation.constraints.Min;
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

        @NotNull
        @Min(value = 1, message = "Parallelism must be at least 1")
        private Integer parallelism = 2;

        private List<String> additionalArgs = new ArrayList<>();

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
        @NotNull
        private Integer retryAttempts = 3;

        public boolean shouldUseApiKey() {
            return apiKey != null && !apiKey.isBlank();
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
