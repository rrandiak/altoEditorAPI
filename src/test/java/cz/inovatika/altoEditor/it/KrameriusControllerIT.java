package cz.inovatika.altoEditor.it;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Path;
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

import cz.inovatika.altoEditor.domain.enums.Role;
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

    private final String TEST_PID = "uuid:12345678-1234-1234-1234-1234567890ab";
    private final String TEST_INSTANCE = "test";

    @TempDir
    static Path storeDir;

    @DynamicPropertySource
    static void registerTempDirs(DynamicPropertyRegistry registry) {
        registry.add("application.store.path", () -> storeDir.toString());
    }

    // Helper to inject a UserProfile as principal
    private static RequestPostProcessor userProfile(String username, Integer userId, List<Role> roles) {
        UserProfile profile = new UserProfile("dummy-token", userId, null, username, roles);
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
}
