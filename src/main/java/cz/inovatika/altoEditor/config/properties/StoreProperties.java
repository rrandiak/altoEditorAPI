package cz.inovatika.altoEditor.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Validated
@ConfigurationProperties(prefix = "application.store")
public class StoreProperties {
    @NotNull
    private String path;
    @NotNull
    private String pattern = "xx";
    @NotNull
    private int unmarshallerPoolSize = 10;

    public String getNormalizedPattern() {
        return pattern.replaceAll("x", "#");
    }
}