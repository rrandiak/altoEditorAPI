package cz.inovatika.altoEditor.it;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.hibernate.search.mapper.orm.session.SearchSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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

import cz.inovatika.altoEditor.domain.enums.AltoVersionState;
import cz.inovatika.altoEditor.domain.enums.Role;
import cz.inovatika.altoEditor.domain.model.AltoVersion;
import cz.inovatika.altoEditor.domain.model.DigitalObject;
import cz.inovatika.altoEditor.domain.model.User;
import cz.inovatika.altoEditor.domain.repository.AltoVersionRepository;
import cz.inovatika.altoEditor.domain.repository.DigitalObjectRepository;
import cz.inovatika.altoEditor.domain.repository.UserRepository;
import cz.inovatika.altoEditor.infrastructure.kramerius.KrameriusService;
import cz.inovatika.altoEditor.infrastructure.kramerius.model.KrameriusObjectMetadata;
import cz.inovatika.altoEditor.infrastructure.storage.AkubraService;
import cz.inovatika.altoEditor.presentation.security.UserProfile;
import jakarta.persistence.EntityManager;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = "altoeditor.home=src/test/resources")
class AltoVersionControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DigitalObjectRepository digitalObjectRepository;

    @Autowired
    private AltoVersionRepository altoVersionRepository;

    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private KrameriusService krameriusService;

    @Autowired
    private AkubraService akubraService;

    @Autowired
    private EntityManager entityManager;

    private static final String TEST_UUID = "12345678-1234-1234-1234-1234567890ab";
    private static final String TEST_PID = "uuid:" + TEST_UUID;
    private static final String OTHER_PID = "uuid:87654321-4321-4321-4321-210987654321";
    private static final int TEST_VERSION = 0;
    private static final String TEST_ALTO_XML = """
            <alto xmlns="http://www.loc.gov/standards/alto/ns-v2#">
              <Layout>
                <Page ID="P1" PHYSICAL_IMG_NR="1">
                  <PrintSpace HEIGHT="1000" WIDTH="800" HPOS="0" VPOS="0">
                    <TextBlock ID="TB1" HEIGHT="200" WIDTH="600" HPOS="100" VPOS="100">
                      <TextLine HEIGHT="50" WIDTH="500" HPOS="150" VPOS="150">
                        <String CONTENT="Test OCR content"/>
                      </TextLine>
                    </TextBlock>
                  </PrintSpace>
                </Page>
              </Layout>
            </alto>
            """;
    private static final byte[] TEST_ALTO = TEST_ALTO_XML.getBytes(StandardCharsets.UTF_8);
    private static final byte[] TEST_OCR = "Test OCR content".getBytes(StandardCharsets.UTF_8);
    private static final String TEST_INSTANCE = "test";
    private static final byte[] TEST_IMAGE = new byte[] { 1, 2, 3 };
    private static final String CONTENT_HASH = "a1b2c3d4e5f6";

    private DigitalObject testDigitalObject;
    private AltoVersion testAltoVersion;
    private User testUser;

    @TempDir
    static java.nio.file.Path storeDir;

    @DynamicPropertySource
    static void registerTempDirs(DynamicPropertyRegistry registry) {
        registry.add("application.store.path", () -> storeDir.toString());
    }

    private RequestPostProcessor userProfile() {
        return userProfileWithRoles(List.of(Role.CURATOR));
    }

    private RequestPostProcessor userProfileWithRoles(List<Role> roles) {
        Long userId = testUser.getId();
        String username = testUser.getUsername();
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
        Mockito.when(krameriusService.getImageBytes(Mockito.eq(TEST_PID), Mockito.any()))
                .thenReturn(TEST_IMAGE);
        Mockito.when(krameriusService.getImageBytes(Mockito.eq(OTHER_PID), Mockito.any()))
                .thenReturn(TEST_IMAGE);
    }

    @BeforeEach
    @Transactional
    void setUpData() throws InterruptedException {
        altoVersionRepository.deleteAll();
        digitalObjectRepository.deleteAll();
        userRepository.deleteAll();
        entityManager.flush();
        entityManager.clear();

        testUser = userRepository.save(User.builder().username("testuser").build());
        User altoUser = userRepository.save(User.builder().username("altoeditor").build());
        userRepository.save(User.builder().username("pero").engine(true).build());
        userRepository.save(User.builder().username("test").build());

        testDigitalObject = digitalObjectRepository.save(DigitalObject.builder().pid(TEST_PID).build());

        testAltoVersion = altoVersionRepository.save(AltoVersion.builder()
                .digitalObject(testDigitalObject)
                .version(TEST_VERSION)
                .state(AltoVersionState.PENDING)
                .user(testUser)
                .contentHash(CONTENT_HASH)
                .presentInInstances(new HashSet<>(Set.of(TEST_INSTANCE)))
                .build());

        altoVersionRepository.save(AltoVersion.builder()
                .digitalObject(testDigitalObject)
                .version(TEST_VERSION + 1)
                .state(AltoVersionState.ACTIVE)
                .user(altoUser)
                .contentHash(CONTENT_HASH)
                .presentInInstances(new HashSet<>(Set.of(TEST_INSTANCE)))
                .build());

        entityManager.flush();
        SearchSession searchSession = org.hibernate.search.mapper.orm.Search.session(entityManager);
        searchSession.massIndexer(AltoVersion.class).startAndWait();
    }

    @Nested
    @DisplayName("GET /api/alto-versions/search")
    class GetAltoVersionsSearch {

        @Test
        @DisplayName("returns 200 and paginated results for CURATOR")
        void returnsOkAndPage() throws Exception {
            mockMvc.perform(get("/api/alto-versions/search")
                    .with(userProfile())
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items").isArray())
                    .andExpect(jsonPath("$.total").isNumber());
        }
    }

    @Nested
    @DisplayName("GET /api/alto-versions/{pid}/related")
    class GetRelatedAlto {

        @Test
        @DisplayName("returns 200 and ALTO when user has version")
        void returnsOkAndContent_whenUserHasVersion() throws Exception {
            akubraService.saveAltoContent(TEST_PID, TEST_VERSION, TEST_ALTO);
            mockMvc.perform(get("/api/alto-versions/" + TEST_PID + "/related")
                    .with(userProfile())
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.pid").value(TEST_PID));
        }

        @Test
        @DisplayName("returns 200 when no related version and Kramerius ALTO is stubbed")
        void returnsOk_whenFetchesNewFromKramerius() throws Exception {
            Mockito.when(krameriusService.getAltoBytes(Mockito.eq(OTHER_PID), Mockito.any()))
                    .thenReturn(TEST_ALTO);
            KrameriusObjectMetadata meta = KrameriusObjectMetadata.builder()
                    .pid(OTHER_PID)
                    .model("page")
                    .title("Page")
                    .level(1)
                    .indexInParent(0)
                    .parentPid(null)
                    .rootPid(OTHER_PID)
                    .build();
            Mockito.when(krameriusService.getObjectMetadata(Mockito.eq(OTHER_PID), Mockito.any()))
                    .thenReturn(meta);
            mockMvc.perform(get("/api/alto-versions/" + OTHER_PID + "/related")
                    .param("instanceId", TEST_INSTANCE)
                    .with(userProfile())
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.pid").value(OTHER_PID));
        }
    }

    @Nested
    @DisplayName("GET /api/alto-versions/search/related")
    class GetUserAltoVersions {

        @Test
        @DisplayName("returns 200 and paginated results for EDITOR/CURATOR")
        void returnsOkAndPage() throws Exception {
            mockMvc.perform(get("/api/alto-versions/search/related")
                    .with(userProfile())
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items").isArray())
                    .andExpect(jsonPath("$.total").isNumber());
        }
    }

    @Nested
    @DisplayName("GET /api/alto-versions/{pid}/versions/{version}")
    class GetAltoVersion {

        @Test
        @DisplayName("returns 200 and ALTO for specific version")
        void returnsOkAndContent() throws Exception {
            akubraService.saveAltoContent(TEST_PID, TEST_VERSION, TEST_ALTO);
            mockMvc.perform(get("/api/alto-versions/" + TEST_PID + "/versions/" + TEST_VERSION)
                    .with(userProfile())
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.pid").value(TEST_PID))
                    .andExpect(jsonPath("$.version").value(TEST_VERSION));
        }
    }

    @Nested
    @DisplayName("GET /api/alto-versions/{pid}/active")
    class GetActiveAlto {

        @Test
        @DisplayName("returns 200 and active ALTO content")
        void returnsOkAndContent() throws Exception {
            akubraService.saveAltoContent(TEST_PID, 0, TEST_ALTO);
            akubraService.saveAltoContent(TEST_PID, 1, TEST_ALTO);
            mockMvc.perform(get("/api/alto-versions/" + TEST_PID + "/active")
                    .with(userProfile())
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.pid").value(TEST_PID));
        }
    }

    @Nested
    @DisplayName("GET /api/alto-versions/{versionId}/ocr")
    class GetAltoVersionOcr {

        @Test
        @DisplayName("returns 200 and OCR text")
        void returnsOkAndContent() throws Exception {
            akubraService.saveAltoContent(TEST_PID, TEST_VERSION, TEST_ALTO);
            mockMvc.perform(get("/api/alto-versions/" + testAltoVersion.getId() + "/ocr")
                    .with(userProfile())
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("GET /api/alto-versions/{pid}/image")
    class GetImage {

        @Test
        @DisplayName("returns 200 and image bytes")
        void returnsOk() throws Exception {
            mockMvc.perform(get("/api/alto-versions/" + TEST_PID + "/image")
                    .with(userProfile())
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("POST /api/alto-versions/{pid}/versions")
    class NewAltoVersion {

        @Test
        @DisplayName("returns 200 and updated ALTO version")
        void returnsOkAndVersion() throws Exception {
            mockMvc.perform(post("/api/alto-versions/" + TEST_PID + "/versions")
                    .with(userProfile())
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .content(TEST_ALTO))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.pid").value(TEST_PID));
        }
    }

    @Nested
    @DisplayName("POST /api/alto-versions/{pid}/generate/{engine}")
    class GenerateAlto {

        @Test
        @DisplayName("returns 200 and batch for PID without existing ALTO")
        void returnsOkAndBatch() throws Exception {
            mockMvc.perform(post("/api/alto-versions/" + OTHER_PID + "/generate/pero")
                    .with(userProfile())
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.pid").value(OTHER_PID))
                    .andExpect(jsonPath("$.engine").value("pero"));
        }
    }

    @Nested
    @DisplayName("POST /api/alto-versions/{versionId}/accept")
    class Accept {

        @Test
        @DisplayName("returns 200 and uploads ALTO/OCR to Kramerius")
        void returnsOkAndUploadsToKramerius() throws Exception {
            akubraService.saveAltoContent(TEST_PID, testAltoVersion.getVersion(), TEST_ALTO);
            akubraService.saveOcrContent(TEST_PID, testAltoVersion.getVersion(), TEST_OCR);

            mockMvc.perform(post("/api/alto-versions/" + testAltoVersion.getId() + "/accept")
                    .with(userProfile())
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk());

            Mockito.verify(krameriusService).uploadAltoOcr(
                    Mockito.eq(TEST_PID),
                    Mockito.argThat(bytes -> java.util.Arrays.equals(bytes, TEST_ALTO)),
                    Mockito.argThat(bytes -> java.util.Arrays.equals(bytes, TEST_OCR)));
        }
    }

    @Nested
    @DisplayName("POST /api/alto-versions/{versionId}/reject")
    class Reject {

        @Test
        @DisplayName("returns 200 for PENDING version")
        void returnsOk() throws Exception {
            mockMvc.perform(post("/api/alto-versions/" + testAltoVersion.getId() + "/reject")
                    .with(userProfile())
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("POST /api/alto-versions/{versionId}/archive")
    class Archive {

        @Test
        @DisplayName("returns 200 for PENDING version")
        void returnsOk() throws Exception {
            mockMvc.perform(post("/api/alto-versions/" + testAltoVersion.getId() + "/archive")
                    .with(userProfile())
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk());
        }
    }
}
