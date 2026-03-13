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
    @NotNull
    private Compression compression = Compression.ZSTD;
    @NotNull
    private int compressionLevel = 3;

    public enum Compression {
        NONE,
        ZSTD
    }

    public String getNormalizedPattern() {
        return pattern.replaceAll("x", "#");
    }
}