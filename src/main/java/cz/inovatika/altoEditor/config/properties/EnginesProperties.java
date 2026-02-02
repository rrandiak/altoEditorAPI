package cz.inovatika.altoEditor.config.properties;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
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

        @NotBlank
        private String inImageArg = "-i";
        @NotBlank
        private String outAltoArg = "-oA";
        @NotBlank
        private String outOcrArg = "-oO";

        private List<String> additionalArgs = new ArrayList<>();

        @NotNull
        private Long timeout = 180_000L;
    }
}
