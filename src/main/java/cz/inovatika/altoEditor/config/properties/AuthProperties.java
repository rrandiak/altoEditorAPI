package cz.inovatika.altoEditor.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Validated
@ConfigurationProperties(prefix = "application.auth-kramerius")
public class AuthProperties {
    @NotNull
    private String url;
    @NotNull
    private String userInfo = "/search/api/client/v7.0/user";

    public String getUserInfoUrl() {
        return url + userInfo;
    }
}