package cz.inovatika.altoEditor.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Validated
@ConfigurationProperties(prefix = "application.permission")
public class PermissionProperties {
    @NotNull
    private String editor = "AltoEditor";
    @NotNull
    private String curator = "AltoCurator";
}