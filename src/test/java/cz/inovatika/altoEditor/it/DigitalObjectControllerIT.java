package cz.inovatika.altoEditor.it;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
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
import cz.inovatika.altoEditor.domain.model.User;
import cz.inovatika.altoEditor.domain.repository.DigitalObjectRepository;
import cz.inovatika.altoEditor.domain.repository.UserRepository;
import cz.inovatika.altoEditor.infrastructure.kramerius.KrameriusService;
import cz.inovatika.altoEditor.infrastructure.storage.AkubraService;
import cz.inovatika.altoEditor.presentation.security.UserProfile;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = "altoeditor.home=src/test/resources")
public class DigitalObjectControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DigitalObjectRepository digitalObjectRepository;

    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private KrameriusService krameriusService;

    @Autowired
    private AkubraService akubraService;

    private static final String TEST_PID = "uuid:12345678-1234-1234-1234-1234567890ab";
    private static final int TEST_VERSION = 0;
    private static final String TEST_ALTO_XML = "<alto><Layout><Page><PrintSpace><TextBlock><TextLine><String CONTENT=\"Test OCR content\"/></TextLine></TextBlock></PrintSpace></Page></Layout></alto>";
    private static final byte[] TEST_ALTO = TEST_ALTO_XML.getBytes(StandardCharsets.UTF_8);
    private final String TEST_ALTO_PATH = "05/61/38/info%3Afedora%2Fuuid%3A12345678-1234-1234-1234-1234567890ab%2FALTO%2FALTO.0";
    private final String TEST_OCR_PATH = "bd/af/f9/info%3Afedora%2Fuuid%3A12345678-1234-1234-1234-1234567890ab%2FTEXT%5FOCR%2FTEXT%5FOCR.0";
    private final String TEST_INSTANCE = "test";
    private static final byte[] TEST_IMAGE = new byte[] { 1, 2, 3 };

    private DigitalObject testDigitalObject;
    private User testUser;

    @TempDir
    static Path storeDir;

    @DynamicPropertySource
    static void registerTempDirs(DynamicPropertyRegistry registry) {
        registry.add("application.store.path", () -> storeDir.toString());
    }

    // Helper to inject a UserProfile as principal
    private RequestPostProcessor userProfile() {
        Integer userId = testUser.getId();
        String username = testUser.getUsername();
        List<Role> roles = List.of(Role.CURATOR);
        UserProfile profile = new UserProfile("dummy-token", userId, null, username, roles);
        List<SimpleGrantedAuthority> authorities = roles.stream()
                .map(r -> new SimpleGrantedAuthority(r.toString()))
                .toList();
        Authentication auth = new UsernamePasswordAuthenticationToken(profile, "dummy-token", authorities);
        return SecurityMockMvcRequestPostProcessors.authentication(auth);
    }

    @BeforeEach
    void setupKrameriusServiceMock() {
        Mockito.reset(krameriusService);
        Mockito.when(krameriusService.getImageBytes(Mockito.eq(TEST_PID), Mockito.any(), Mockito.any()))
                .thenReturn(TEST_IMAGE);
    }

    @BeforeEach
    void setUpData() {
        userRepository.deleteAll();
        testUser = userRepository.save(User.builder().username("testuser").build());

        User altoUser = userRepository.save(User.builder().username("altoeditor").build());
        userRepository.save(User.builder().username("pero").build());

        digitalObjectRepository.deleteAll();
        testDigitalObject = digitalObjectRepository.save(DigitalObject.builder()
                .pid(TEST_PID)
                .version(TEST_VERSION)
                .label("Test Label")
                .state(DigitalObjectState.PENDING)
                .instanceId("test")
                .user(testUser)
                .build());

        digitalObjectRepository.save(DigitalObject.builder()
                .pid(TEST_PID)
                .version(TEST_VERSION + 1)
                .label("Test Label")
                .state(DigitalObjectState.ACTIVE)
                .instanceId("test")
                .user(altoUser)
                .build());
    }

    @Test
    void getDigitalObjects_returnsOkAndPage() throws Exception {
        mockMvc.perform(get("/api/objects/search-all")
                .with(userProfile())
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].pid").value(TEST_PID));
    }

    @Test
    void getRelatedAlto_returnsOkAndContent() throws Exception {
        akubraService.saveAltoContent(TEST_PID, 0, TEST_ALTO);
        mockMvc.perform(get("/api/objects/" + TEST_PID + "/related-alto")
                .with(userProfile())
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pid").value(TEST_PID));
    }

    @Test
    void getRelatedAlto_fetchesNewVersion_returnsOkAndContent() throws Exception {
        mockMvc.perform(get("/api/objects/" + TEST_PID + "/related-alto")
                .with(userProfile())
                .contentType(MediaType.APPLICATION_JSON)
                .content(TEST_ALTO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pid").value(TEST_PID));
    }

    @Test
    void getDigitalObjectAltoOriginal_returnsOkAndContent() throws Exception {
        akubraService.saveAltoContent(TEST_PID, 0, TEST_ALTO);
        akubraService.saveAltoContent(TEST_PID, 1, TEST_ALTO);
        mockMvc.perform(get("/api/objects/" + TEST_PID + "/active-alto")
                .with(userProfile())
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pid").value(TEST_PID));
    }

    @Test
    void getDigitalObjectOcr_returnsOkAndContent() throws Exception {
        akubraService.saveAltoContent(TEST_PID, 0, TEST_ALTO);
        mockMvc.perform(get("/api/objects/" + testDigitalObject.getId() + "/ocr")
                .with(userProfile())
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void getKrameriusObjectImage_returnsOk() throws Exception {
        String imageUrl = "/api/objects/" + TEST_PID + "/image";
        mockMvc.perform(get(imageUrl)
                .with(userProfile())
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void generateAlto_returnsOkAndBatch() throws Exception {
        mockMvc.perform(post("/api/objects/" + TEST_PID + "/generate")
                .with(userProfile())
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void setDigitalObjectActive_returnsOk() throws Exception {
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

        mockMvc.perform(post("/api/objects/" + testDigitalObject.getId() + "/set-active")
                .with(userProfile())
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
    void rejectDigitalObject_returnsOk() throws Exception {
        mockMvc.perform(post("/api/objects/" + testDigitalObject.getId() + "/reject")
                .with(userProfile())
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }
}
