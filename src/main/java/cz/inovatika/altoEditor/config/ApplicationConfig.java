package cz.inovatika.altoEditor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import lombok.Data;

@Data
@Configuration
@ConfigurationProperties(prefix = "application")
public class ApplicationConfig {

    private String version;
    private Integer port = 8080;

    private JdbcConfig jdbc = new JdbcConfig();
    private StoreConfig objectStore = new StoreConfig();
    private StoreConfig dataStreamStore = new StoreConfig();
    private AuthKrameriusConfig authKramerius = new AuthKrameriusConfig();
    private PermissionConfig permission = new PermissionConfig();

    private int maxProcesses = 5;
    private String workDir = "/tmp/altoEditor";

    @Data
    public static class JdbcConfig {
        private String driver = "org.postgresql.Driver";
        private String url;
        private String username;
        private String password;
        private Integer poolSize = 10;
    }

    @Data
    public static class StoreConfig {
        private String pattern = "xx";
        private String path;

        public String getNormalizedPattern() {
            return pattern.replaceAll("x", "#");
        }
    }

    @Data
    public static class AuthKrameriusConfig {
        private String url;
        private String userInfo = "/search/api/client/v7.0/user";

        public String getUserInfoUrl() {
            return url + userInfo;
        }
    }

    @Data
    public static class PermissionConfig {
        private String editor;
        private String curator;
    }
}
