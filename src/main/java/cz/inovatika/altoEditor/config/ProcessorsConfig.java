package cz.inovatika.altoEditor.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import lombok.Data;

@Data
@Configuration
@ConfigurationProperties(prefix = "processors")
public class ProcessorsConfig {

    private Map<String, ProcessorConfig> processors = new HashMap<>();

    @Data
    public static class ProcessorConfig {
        private String exec;
        private String entry;

        private String inImageArg = "-i";
        private String outAltoArg = "-oA";
        private String outOcrArg = "-oO";

        private List<String> additionalArgs = new ArrayList<>();

        private Long timeout = 180_000L;
    }
}
