package cz.inovatika.altoEditor.config;

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import lombok.Data;

@Data
@Configuration
@ConfigurationProperties
public class KrameriusConfig {
    private Map<String, KrameriusInstance> krameriusInstances = new HashMap<>();

    @Data
    public static class KrameriusInstance {
        private String title;
        private KrameriusVersion version = KrameriusVersion.V7;
        private String url;
        private String type;
        private String adminUrl;

        public String trimmedUrl() {
            return url != null ? url.replaceAll("/+$", "") : null;
        }

        public String buildEndpoint(String endpoint) {
            if (endpoint == null || endpoint.isBlank()) {
                throw new IllegalArgumentException("Endpoint must not be null or blank");
            }
            return trimmedUrl() + (endpoint.startsWith("/") ? endpoint : "/" + endpoint);
        }
    }

    public String getDefaultInstanceId() {
        return krameriusInstances.keySet().stream().findFirst()
                .orElseThrow(() -> new RuntimeException("No Kramerius instance configured"));
    }

    public enum KrameriusVersion {
        V7
    }
}
