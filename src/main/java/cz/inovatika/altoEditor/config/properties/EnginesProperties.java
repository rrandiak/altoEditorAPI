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

        private String inImageArg = "-i";
        private String outAltoArg = "-oA";
        private String outOcrArg = "-oO";
        private Boolean batchMode = false;
        private String dataTripletsArg = "-t";

        private List<String> additionalArgs = new ArrayList<>();

        /**
         * When batchMode is false: inImageArg, outAltoArg and outOcrArg must be set.
         * When batchMode is true: dataTripletsArg must be set.
         */
        @AssertTrue(message = """
                When batchMode is false, inImageArg, outAltoArg and outOcrArg must be set;
                when batchMode is true, dataTripletsArg must be set
                """)
        public boolean isValidEngineArgs() {
            if (Boolean.TRUE.equals(batchMode)) {
                return dataTripletsArg != null && !dataTripletsArg.isBlank();
            }
            return inImageArg != null && !inImageArg.isBlank()
                    && outAltoArg != null && !outAltoArg.isBlank()
                    && outOcrArg != null && !outOcrArg.isBlank();
        }

        @NotNull
        private Integer batchSize = 100;

        @NotNull
        private Long timeout = 180_000L;

        public boolean isBatchMode() {
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
