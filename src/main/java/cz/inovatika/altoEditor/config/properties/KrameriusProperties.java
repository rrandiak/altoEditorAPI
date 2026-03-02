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
        private String loginType = "form";

        @NotBlank
        private String serviceClientId;
        @NotBlank
        private String serviceSecret;

        @NotNull
        private Integer connectTimeout = 10000;
        @NotNull
        private Integer readTimeout = 30000;
        @NotNull
        private Integer maxConnections = 32;
        @NotNull
        private Integer maxConnectionIdleTime = 30000;
        @NotNull
        private Integer pendingConnectionAcquireTimeout = 5000;

        @NotNull
        private Integer indexBatchSize = 1000;

        private String clientUrl;
        private String adminUrl;
        private String defaultLanguage = "cs";

        public String getLoginUrl() {
            return this.buildEndpoint(version.loginUrl(loginType));
        }

        public String getLogoutUrl() {
            return this.buildEndpoint(version.logoutUrl());
        }

        public String getExchangeCodeForTokenUrl() {
            return this.buildEndpoint(version.exchangeCodeForTokenUrl());
        }

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

    public String getDefaultInstance() {
        return krameriusInstances.keySet().stream().findFirst()
                .orElseThrow(() -> new RuntimeException("No Kramerius instance configured"));
    }

    public enum KrameriusVersion {
        V7;

        private String build(String endpoint, String... params) {
            StringBuilder sb = new StringBuilder(endpoint);
            for (String param : params) {
                sb.append("&");
                sb.append(param);
            }
            return sb.toString();
        }

        public String loginUrl(String loginType) {
            if (this.equals(KrameriusVersion.V7)) {
                return this.build("/search/api/client/v7.0/user/auth/login", "loginType", loginType, "redirect_uri",
                        "${redirectUri}");
            }

            return null;
        }

        public String logoutUrl() {
            if (this.equals(KrameriusVersion.V7)) {
                return this.build("/search/api/client/v7.0/user/auth/logout", "redirect_uri", "${redirectUri}");
            }

            return null;
        }

        public String exchangeCodeForTokenUrl() {
            if (this.equals(KrameriusVersion.V7)) {
                return this.build("/search/api/client/v7.0/user/auth/token", "code", "${code}", "redirect_uri",
                        "${redirectUri}");
            }

            return null;
        }

    }

}
