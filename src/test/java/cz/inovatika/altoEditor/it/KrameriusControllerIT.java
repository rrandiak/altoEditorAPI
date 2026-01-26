package cz.inovatika.altoEditor.it;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import cz.inovatika.altoEditor.domain.enums.DigitalObjectState;
import cz.inovatika.altoEditor.domain.enums.Role;
import cz.inovatika.altoEditor.domain.model.DigitalObject;
import cz.inovatika.altoEditor.domain.repository.DigitalObjectRepository;
import cz.inovatika.altoEditor.infrastructure.kramerius.KrameriusService;
import cz.inovatika.altoEditor.infrastructure.kramerius.model.KrameriusObjectMetadata;
import cz.inovatika.altoEditor.presentation.security.UserProfile;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = "altoeditor.home=src/test/resources")
public class KrameriusControllerIT {

    @MockitoBean
    private KrameriusService krameriusService;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DigitalObjectRepository digitalObjectRepository;

    private final String TEST_PID = "uuid:12345678-1234-1234-1234-1234567890ab";
    private final String TEST_ALTO_PATH = "05/61/38/info%3Afedora%2Fuuid%3A12345678-1234-1234-1234-1234567890ab%2FALTO%2FALTO.0";
    private final String TEST_OCR_PATH = "bd/af/f9/info%3Afedora%2Fuuid%3A12345678-1234-1234-1234-1234567890ab%2FTEXT%5FOCR%2FTEXT%5FOCR.0";
    private final String TEST_INSTANCE = "test";
    private Integer testObjectId = null;

    @TempDir
    static Path storeDir;

    @DynamicPropertySource
    static void registerTempDirs(DynamicPropertyRegistry registry) {
        registry.add("application.store.path", () -> storeDir.toString());
    }

    // Helper to inject a UserProfile as principal
    private static RequestPostProcessor userProfile(String username, Integer userId, List<Role> roles) {
        UserProfile profile = new UserProfile("dummy-token", userId, username, roles);
        List<SimpleGrantedAuthority> authorities = roles.stream().map(r -> new SimpleGrantedAuthority(r.toString()))
                .toList();
        Authentication auth = new UsernamePasswordAuthenticationToken(profile, "dummy-token", authorities);
        return authentication(auth);
    }

    @BeforeEach
    void setupRestTemplateMock() {
        // Mock KrameriusService to return a DTO directly
        KrameriusObjectMetadata mockDto = KrameriusObjectMetadata.builder()
                .pid(TEST_PID)
                .title("Test Title")
                .parentPath("/parent/path").parentTitle("Parent Title")
                .build();

        Mockito.when(krameriusService.getObjectMetadata(
                ArgumentMatchers.eq(TEST_PID),
                ArgumentMatchers.any(),
                ArgumentMatchers.any())).thenReturn(mockDto);
    }

    @BeforeEach
    void setupData() {
        // Create a digital object in the DB for upload endpoint
        digitalObjectRepository.deleteAll();
        testObjectId = digitalObjectRepository.save(DigitalObject.builder()
                .pid(TEST_PID)
                .instanceId(TEST_INSTANCE)
                .userId(1)
                .version(0)
                .state(DigitalObjectState.EDITED)
                .build()).getId();
    }

    @Test
    void getKrameriusObject_returnsMetadata_withDefaultInstance() throws Exception {
        mockMvc.perform(get("/api/kramerius/objects/" + TEST_PID)
                .with(userProfile("curator", 1, List.of(Role.CURATOR)))
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pid").value(TEST_PID))
                .andExpect(jsonPath("$.title").value("Test Title"));
    }

    @Test
    void getKrameriusObject_returnsMetadata_withCorrectInstance() throws Exception {
        mockMvc.perform(get("/api/kramerius/objects/" + TEST_PID)
                .with(userProfile("curator", 1, List.of(Role.CURATOR)))
                .param("instanceId", TEST_INSTANCE)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pid").value(TEST_PID))
                .andExpect(jsonPath("$.title").value("Test Title"));
    }

    @Test
    void getKrameriusObject_forbiddenForUnauthorized() throws Exception {
        mockMvc.perform(get("/api/kramerius/objects/" + TEST_PID)
                .param("instanceId", TEST_INSTANCE))
                .andExpect(status().isForbidden());
    }

    @Test
    void uploadObjectToKramerius_returnsOk() throws Exception {
        File altoFile = storeDir.resolve(TEST_ALTO_PATH).toFile();
        altoFile.getParentFile().mkdirs();
        byte[] altoContent = new byte[] { 0x41, 0x4C, 0x54, 0x4F }; // "ALTO" in ASCII
        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(altoFile)) {
            fos.write(altoContent);
        }

        File ocrFile = storeDir.resolve(TEST_OCR_PATH).toFile();
        ocrFile.getParentFile().mkdirs();
        byte[] ocrContent = new byte[] { 0x4F, 0x43, 0x52 }; // "OCR" in ASCII
        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(ocrFile)) {
            fos.write(ocrContent);
        }

        mockMvc.perform(post("/api/kramerius/objects/upload/" + testObjectId)
                .with(userProfile("curator", 1, List.of(Role.CURATOR)))
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        // Verify that KrameriusService.uploadAltoOcr was called with the expected
        // content
        Mockito.verify(krameriusService).uploadAltoOcr(
                Mockito.eq(TEST_PID),
                Mockito.eq(TEST_INSTANCE),
                Mockito.argThat(bytes -> Arrays.equals(bytes, altoContent)),
                Mockito.argThat(bytes -> Arrays.equals(bytes, ocrContent)),
                Mockito.any());
    }

    @Test
    void uploadObjectToKramerius_forbiddenForUnauthorized() throws Exception {
        mockMvc.perform(post("/api/kramerius/" + testObjectId + "/upload"))
                .andExpect(status().isForbidden());
    }
}
