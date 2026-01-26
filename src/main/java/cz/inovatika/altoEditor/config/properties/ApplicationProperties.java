package cz.inovatika.altoEditor.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Validated
@ConfigurationProperties(prefix = "application")
public class ApplicationProperties {
    private String version;
    @NotNull
    private Integer port = 8080;
    @NotNull
    private Integer maxProcesses = 5;
    @NotNull
    private String workDir = "/tmp/altoEditor";
}