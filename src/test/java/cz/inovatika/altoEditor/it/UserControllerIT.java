package cz.inovatika.altoEditor.it;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import cz.inovatika.altoEditor.domain.enums.Role;
import cz.inovatika.altoEditor.domain.model.User;
import cz.inovatika.altoEditor.domain.repository.UserRepository;
import cz.inovatika.altoEditor.presentation.security.UserProfile;

import java.nio.file.Path;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = "altoeditor.home=src/test/resources")
class UserControllerIT {

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

    /** User that exists in DB; use for GET /me (profile has userId). */
    private User existingUser;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        existingUser = userRepository.save(User.builder()
                .username("user1")
                .uid("uid-user1")
                .kramerius(false)
                .engine(false)
                .enabled(true)
                .build());
        userRepository.save(User.builder()
                .username("user2")
                .uid("uid-user2")
                .kramerius(false)
                .engine(false)
                .enabled(true)
                .build());
        userRepository.save(User.builder()
                .username("kramerius-inst")
                .uid("uid-kramerius")
                .kramerius(true)
                .engine(false)
                .enabled(true)
                .build());
        userRepository.save(User.builder()
                .username("engine-pero")
                .uid("uid-pero")
                .kramerius(false)
                .engine(true)
                .enabled(true)
                .build());
        userRepository.save(User.builder()
                .username("disabled-user")
                .uid("uid-disabled")
                .kramerius(false)
                .engine(false)
                .enabled(false)
                .build());
    }

    private RequestPostProcessor asCurator() {
        return user("curator").authorities(new SimpleGrantedAuthority("CURATOR"));
    }

    private RequestPostProcessor asEditor() {
        return user("editor").authorities(new SimpleGrantedAuthority("EDITOR"));
    }

    /** UserProfile with existing DB user (for GET /me). */
    private RequestPostProcessor userProfileMe() {
        return userProfile(existingUser.getId(), "uid-user1", "user1", List.of(Role.CURATOR));
    }

    /** UserProfile with no DB user yet (for POST /me create). */
    private RequestPostProcessor userProfileNew(String uid, String username) {
        return userProfile(null, uid, username, List.of(Role.CURATOR));
    }

    private RequestPostProcessor userProfile(Long userId, String uid, String username, List<Role> roles) {
        UserProfile profile = new UserProfile("dummy-token", userId, uid, username, roles);
        List<SimpleGrantedAuthority> authorities = roles.stream()
                .map(r -> new SimpleGrantedAuthority(r.toString()))
                .toList();
        var auth = new UsernamePasswordAuthenticationToken(profile, "dummy-token", authorities);
        return SecurityMockMvcRequestPostProcessors.authentication(auth);
    }

    @Nested
    @DisplayName("GET /api/users")
    class GetUsers {

        @Test
        @DisplayName("returns 200 and paginated users for CURATOR")
        void returnsOkAndPage() throws Exception {
            mockMvc.perform(get("/api/users")
                    .with(asCurator())
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content.length()").value(5))
                    .andExpect(jsonPath("$.totalElements").value(5))
                    .andExpect(jsonPath("$.content[*].username",
                            containsInAnyOrder("user1", "user2", "kramerius-inst", "engine-pero", "disabled-user")));
        }

        @Test
        @DisplayName("returns 200 for EDITOR")
        void returnsOkForEditor() throws Exception {
            mockMvc.perform(get("/api/users")
                    .with(asEditor())
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.totalElements").value(5));
        }

        @Test
        @DisplayName("returns 403 without EDITOR or CURATOR authority")
        void returnsForbidden_withoutAuthority() throws Exception {
            mockMvc.perform(get("/api/users")
                    .with(user("other").authorities(new SimpleGrantedAuthority("OTHER")))
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("filter isKramerius=true returns only Kramerius users")
        void filterIsKramerius_returnsMatching() throws Exception {
            mockMvc.perform(get("/api/users")
                    .param("isKramerius", "true")
                    .with(asCurator())
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].username").value("kramerius-inst"))
                    .andExpect(jsonPath("$.content[0].kramerius").value(true));
        }

        @Test
        @DisplayName("filter isEngine=true returns only engine users")
        void filterIsEngine_returnsMatching() throws Exception {
            mockMvc.perform(get("/api/users")
                    .param("isEngine", "true")
                    .with(asCurator())
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].username").value("engine-pero"))
                    .andExpect(jsonPath("$.content[0].engine").value(true));
        }

        @Test
        @DisplayName("filter isEnabled=false returns only disabled users")
        void filterIsEnabled_returnsMatching() throws Exception {
            mockMvc.perform(get("/api/users")
                    .param("isEnabled", "false")
                    .with(asCurator())
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].username").value("disabled-user"))
                    .andExpect(jsonPath("$.content[0].enabled").value(false));
        }

        @Test
        @DisplayName("pagination respects page and size")
        void pagination_respectsPageAndSize() throws Exception {
            mockMvc.perform(get("/api/users")
                    .param("page", "0")
                    .param("size", "2")
                    .with(asCurator())
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content.length()").value(2))
                    .andExpect(jsonPath("$.size").value(2))
                    .andExpect(jsonPath("$.number").value(0))
                    .andExpect(jsonPath("$.totalElements").value(5));
        }

        @Test
        @DisplayName("returns empty page when no users")
        void returnsEmptyPage_whenNoUsers() throws Exception {
            userRepository.deleteAll();

            mockMvc.perform(get("/api/users")
                    .with(asCurator())
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content.length()").value(0))
                    .andExpect(jsonPath("$.totalElements").value(0));
        }
    }

    @Nested
    @DisplayName("GET /api/users/me")
    class GetMe {

        @Test
        @DisplayName("returns 200 and current user when profile has userId")
        void returnsProfile() throws Exception {
            mockMvc.perform(get("/api/users/me")
                    .with(userProfileMe())
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(existingUser.getId()))
                    .andExpect(jsonPath("$.username").value("user1"));
        }

        @Test
        @DisplayName("returns 403 without authentication")
        void returnsForbidden_withoutAuth() throws Exception {
            mockMvc.perform(get("/api/users/me")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("returns 403 without EDITOR or CURATOR authority")
        void returnsForbidden_withoutAuthority() throws Exception {
            RequestPostProcessor noRole = userProfile(existingUser.getId(), "uid-user1", "user1", List.of());
            mockMvc.perform(get("/api/users/me")
                    .with(noRole)
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("POST /api/users/me")
    class CreateMe {

        @Test
        @DisplayName("creates and returns user when username does not exist")
        void createsProfile() throws Exception {
            mockMvc.perform(post("/api/users/me")
                    .with(userProfileNew("uid-newuser", "newuser"))
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.username").value("newuser"))
                    .andExpect(jsonPath("$.id").isNumber());

            userRepository.findByUsername("newuser").orElseThrow();
        }

        @Test
        @DisplayName("returns 403 without authentication")
        void returnsForbidden_withoutAuth() throws Exception {
            mockMvc.perform(post("/api/users/me")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("returns 403 without EDITOR or CURATOR authority")
        void returnsForbidden_withoutAuthority() throws Exception {
            mockMvc.perform(post("/api/users/me")
                    .with(userProfile(null, "uid-new", "newuser", List.of()))
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isForbidden());
        }
    }
}
