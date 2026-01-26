package cz.inovatika.altoEditor.presentation.rest;

import java.util.Properties;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;

@TestConfiguration
public class TestPropertiesConfig {

    @Bean
    public static PropertySourcesPlaceholderConfigurer testProperties() {
        PropertySourcesPlaceholderConfigurer pspc = new PropertySourcesPlaceholderConfigurer();
        Properties props = new Properties();
        props.setProperty("altoeditor.home", "src/test/resources/");
        pspc.setProperties(props);
        return pspc;
    }
}