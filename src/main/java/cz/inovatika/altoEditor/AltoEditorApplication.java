package cz.inovatika.altoEditor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.PropertySource;

@SpringBootApplication
@PropertySource("file:${altoeditor.home:${user.home}}/application.yml")
public class AltoEditorApplication {

    public static void main(String[] args) {
        SpringApplication.run(AltoEditorApplication.class, args);
    }
}
