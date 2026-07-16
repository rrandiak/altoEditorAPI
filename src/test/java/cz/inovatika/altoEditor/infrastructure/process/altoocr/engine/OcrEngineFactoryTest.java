package cz.inovatika.altoEditor.infrastructure.process.altoocr.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;

import cz.inovatika.altoEditor.config.properties.EngineType;
import cz.inovatika.altoEditor.config.properties.EnginesProperties;
import cz.inovatika.altoEditor.config.properties.EnginesProperties.EngineConfig;
import cz.inovatika.altoEditor.infrastructure.storage.WorkDirectoryService;

@ExtendWith(MockitoExtension.class)
class OcrEngineFactoryTest {

    @Mock
    private EnginesProperties enginesProperties;
    @Mock
    private WorkDirectoryService workDirectoryService;

    private EngineConfig subprocessConfig() {
        EngineConfig c = new EngineConfig();
        c.setType(EngineType.SUBPROCESS);
        c.setExec("python");
        c.setEntry("/x/client.py");
        return c;
    }

    private EngineConfig tuzkaConfig() {
        EngineConfig c = new EngineConfig();
        c.setType(EngineType.TUZKA);
        c.setBaseUrl("http://taas.local");
        c.setApiKey("k");
        return c;
    }

    @Test
    @DisplayName("SUBPROCESS engine resolves to the external-process engine")
    void resolvesSubprocess() {
        when(enginesProperties.getEngineConfig("pero-vut")).thenReturn(subprocessConfig());
        OcrEngineFactory factory = new OcrEngineFactory(enginesProperties, workDirectoryService, new ObjectMapper());

        assertThat(factory.getEngine("pero-vut")).isInstanceOf(ExternalProcessOcrEngine.class);
    }

    @Test
    @DisplayName("TUZKA engine resolves to the tuzka engine and the client is cached")
    void resolvesTuzkaAndCaches() {
        when(enginesProperties.getEngineConfig("tuzka")).thenReturn(tuzkaConfig());
        OcrEngineFactory factory = new OcrEngineFactory(enginesProperties, workDirectoryService, new ObjectMapper());

        OcrEngine first = factory.getEngine("tuzka");
        OcrEngine second = factory.getEngine("tuzka");

        assertThat(first).isInstanceOf(TuzkaOcrEngine.class);
        assertThat(second).isInstanceOf(TuzkaOcrEngine.class);
    }
}
