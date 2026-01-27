package cz.inovatika.altoEditor.it;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import cz.inovatika.altoEditor.domain.enums.Role;
import cz.inovatika.altoEditor.domain.model.User;
import cz.inovatika.altoEditor.domain.repository.UserRepository;
import cz.inovatika.altoEditor.infrastructure.kramerius.KrameriusAuthClient;
import cz.inovatika.altoEditor.infrastructure.kramerius.KrameriusAuthClientFactory;
import cz.inovatika.altoEditor.infrastructure.kramerius.model.KrameriusUser;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = "altoeditor.home=src/test/resources")
public class UserControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @TempDir
    static Path storeDir;

    @DynamicPropertySource
    static void registerTempDirs(DynamicPropertyRegistry registry) {
        registry.add("application.store.path", () -> storeDir.toString());
    }

    @MockitoBean
    private KrameriusAuthClientFactory authClientFactory;

    private void mockKrameriusAuth(String username, List<Role> roles) {
        KrameriusUser mockUser = KrameriusUser.builder()
                .username(username)
                .roles(roles)
                .build();
        KrameriusAuthClient mockClient = Mockito.mock(KrameriusAuthClient.class);
        Mockito.when(mockClient.getUser(Mockito.anyString())).thenReturn(mockUser);
        Mockito.when(authClientFactory.getClient()).thenReturn(mockClient);
    }

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        userRepository.saveAll(List.of(
                User.builder().username("user1").build(),
                User.builder().username("user2").build()
        ));
    }

    @Test
    void getUsers_returnsAllUsers() throws Exception {
        mockMvc.perform(get("/api/users")
                .with(user("user1").authorities(new SimpleGrantedAuthority("CURATOR")))
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[*].username", containsInAnyOrder("user1", "user2")));
    }

    @Test
    void getCurrentUser_returnsProfile() throws Exception {
        mockKrameriusAuth("user1", List.of(Role.CURATOR));
        mockMvc.perform(get("/api/users/me")
                .header("Authorization", "Bearer test-token")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("user1"));
    }

    @Test
    void createCurrentUser_createsProfile() throws Exception {
        mockKrameriusAuth("newuser", List.of(Role.CURATOR));
        mockMvc.perform(post("/api/users/me")
                .header("Authorization", "Bearer test-token")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("newuser"));
    }

    @Test
    void getUsers_forbiddenForUnauthorized() throws Exception {
        mockMvc.perform(get("/api/users"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getCurrentUser_forbiddenForUnauthorized() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isForbidden());
    }

    @Test
    void createCurrentUser_forbiddenForUnauthorized() throws Exception {
        mockMvc.perform(post("/api/users/me"))
                .andExpect(status().isForbidden());
    }
}
