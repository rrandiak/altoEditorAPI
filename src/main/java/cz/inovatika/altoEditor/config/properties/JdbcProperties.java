package cz.inovatika.altoEditor.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Validated
@ConfigurationProperties(prefix = "application.jdbc")
public class JdbcProperties {
    @NotNull
    private String driver = "org.postgresql.Driver";
    @NotNull
    private String url;
    @NotNull
    private String username;
    @NotNull
    private String password;
    @NotNull
    private Integer poolSize = 10;
}