package cz.inovatika.altoEditor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.PropertySource;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@PropertySource("file:${altoeditor.home:${user.home}}/application.yml")
@EnableJpaAuditing
@EnableScheduling
public class AltoEditorApplication {

    public static void main(String[] args) {
        SpringApplication.run(AltoEditorApplication.class, args);
    }
}
