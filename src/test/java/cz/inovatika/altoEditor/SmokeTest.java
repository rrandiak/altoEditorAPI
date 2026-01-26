package cz.inovatika.altoEditor;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import cz.inovatika.altoEditor.config.ApplicationConfig;
import cz.inovatika.altoEditor.config.properties.KrameriusProperties;
import cz.inovatika.altoEditor.config.properties.ProcessorsProperties;
import cz.inovatika.altoEditor.presentation.rest.InfoController;
import cz.inovatika.altoEditor.presentation.security.AuthenticationFilter;

@WebMvcTest(controllers = InfoController.class)
@ImportAutoConfiguration(exclude = {
    AppInitializer.class,
    ApplicationConfig.class,
    KrameriusProperties.class,
    ProcessorsProperties.class
})
@TestPropertySource(properties = "altoeditor.home=src/test/resources")
// @Import(TestPropertiesConfig.class)
class SmokeTest {

    // just to force loading of web context
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthenticationFilter authFilter;

    @Test
    void smokeTest_shouldLoadContext() throws Exception {
        // Just a simple test to check if the context loads
    }
}