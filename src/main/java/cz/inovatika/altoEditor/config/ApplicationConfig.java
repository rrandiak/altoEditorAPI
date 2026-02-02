package cz.inovatika.altoEditor.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import cz.inovatika.altoEditor.config.properties.ApplicationProperties;
import cz.inovatika.altoEditor.config.properties.AuthProperties;
import cz.inovatika.altoEditor.config.properties.JdbcProperties;
import cz.inovatika.altoEditor.config.properties.KrameriusProperties;
import cz.inovatika.altoEditor.config.properties.PermissionProperties;
import cz.inovatika.altoEditor.config.properties.EnginesProperties;
import cz.inovatika.altoEditor.config.properties.StoreProperties;

@Configuration
@EnableConfigurationProperties({
    ApplicationProperties.class,
    AuthProperties.class,
    JdbcProperties.class,
    KrameriusProperties.class,
    PermissionProperties.class,
    EnginesProperties.class,
    StoreProperties.class
})
public class ApplicationConfig {
    // This class only enables the config beans
}
