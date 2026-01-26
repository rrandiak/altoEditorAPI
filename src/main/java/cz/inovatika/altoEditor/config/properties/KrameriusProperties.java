package cz.inovatika.altoEditor.config.properties;

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Validated
@ConfigurationProperties(prefix = "application")
public class KrameriusProperties {
    @Size(min = 1, message = "At least one Kramerius instance must be configured")
    private Map<String, KrameriusInstance> krameriusInstances = new HashMap<>();

    @Data
    public static class KrameriusInstance {
        @NotBlank
        private String title;
        @NotNull
        private KrameriusVersion version = KrameriusVersion.V7;
        @NotBlank
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
