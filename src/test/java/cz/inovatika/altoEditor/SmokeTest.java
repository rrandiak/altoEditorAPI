package cz.inovatika.altoEditor;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "altoeditor.home=src/test/resources")
class SmokeTest {

    // just to force loading of web context
    @Autowired
    private MockMvc mockMvc;

    @Test
    void smokeTest_shouldLoadContext() throws Exception {
        // Just a simple test to check if the context loads
    }
}