package cz.inovatika.altoEditor.it;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import cz.inovatika.altoEditor.domain.enums.BatchPriority;
import cz.inovatika.altoEditor.domain.enums.BatchState;
import cz.inovatika.altoEditor.domain.enums.BatchSubstate;
import cz.inovatika.altoEditor.domain.enums.BatchType;
import cz.inovatika.altoEditor.domain.model.Batch;
import cz.inovatika.altoEditor.domain.model.User;
import cz.inovatika.altoEditor.domain.repository.BatchRepository;
import cz.inovatika.altoEditor.domain.repository.UserRepository;

import java.nio.file.Path;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = "altoeditor.home=src/test/resources")
class BatchControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BatchRepository batchRepository;

    @Autowired
    private UserRepository userRepository;

    @TempDir
    static Path storeDir;

    @DynamicPropertySource
    static void registerTempDirs(DynamicPropertyRegistry registry) {
        registry.add("application.store.path", () -> storeDir.toString());
    }

    private User curatorUser;

    private static final String PID_1 = "uuid:11111111-1111-2222-3333-444444444444";
    private static final String PID_2 = "uuid:22222222-1111-2222-3333-444444444444";

    @BeforeEach
    void setUpData() {
        userRepository.deleteAll();
        batchRepository.deleteAll();

        curatorUser = userRepository.save(User.builder().username("curator").build());

        batchRepository.save(Batch.builder()
                .pid(PID_1)
                .state(BatchState.PLANNED)
                .substate(BatchSubstate.DOWNLOADING)
                .priority(BatchPriority.MEDIUM)
                .type(BatchType.GENERATE_SINGLE)
                .instance("instance1")
                .estimatedItemCount(10)
                .log("Test log 1")
                .createdBy(curatorUser)
                .build());

        batchRepository.save(Batch.builder()
                .pid(PID_2)
                .state(BatchState.RUNNING)
                .substate(BatchSubstate.GENERATING)
                .priority(BatchPriority.HIGH)
                .type(BatchType.RETRIEVE_HIERARCHY)
                .instance("instance2")
                .estimatedItemCount(20)
                .log("Test log 2")
                .createdBy(curatorUser)
                .build());
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor curator() {
        return user("curator").authorities(new SimpleGrantedAuthority("CURATOR"));
    }

    @Nested
    @DisplayName("GET /api/batches")
    class GetBatches {

        @Test
        @DisplayName("returns 200 and paginated batches for CURATOR")
        void returnsOkAndPage() throws Exception {
            mockMvc.perform(get("/api/batches")
                    .with(curator())
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content.length()").value(2))
                    .andExpect(jsonPath("$.totalElements").value(2));
        }

        @Test
        @DisplayName("returns 403 without CURATOR authority")
        void returnsForbidden_withoutCurator() throws Exception {
            mockMvc.perform(get("/api/batches")
                    .with(user("editor").authorities(new SimpleGrantedAuthority("EDITOR")))
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("filter by state returns only matching batches")
        void filterByState_returnsMatching() throws Exception {
            mockMvc.perform(get("/api/batches")
                    .param("state", "PLANNED")
                    .with(curator())
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].state").value("PLANNED"))
                    .andExpect(jsonPath("$.content[0].pid").value(PID_1));
        }

        @Test
        @DisplayName("filter by priority returns only matching batches")
        void filterByPriority_returnsMatching() throws Exception {
            mockMvc.perform(get("/api/batches")
                    .param("priority", "HIGH")
                    .with(curator())
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].priority").value("HIGH"))
                    .andExpect(jsonPath("$.content[0].pid").value(PID_2));
        }

        @Test
        @DisplayName("filter by type returns only matching batches")
        void filterByType_returnsMatching() throws Exception {
            mockMvc.perform(get("/api/batches")
                    .param("type", "GENERATE_SINGLE")
                    .with(curator())
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].type").value("GENERATE_SINGLE"))
                    .andExpect(jsonPath("$.content[0].pid").value(PID_1));
        }

        @Test
        @DisplayName("filter by pid returns only matching batch")
        void filterByPid_returnsMatching() throws Exception {
            mockMvc.perform(get("/api/batches")
                    .param("pid", PID_2)
                    .with(curator())
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].pid").value(PID_2));
        }

        @Test
        @DisplayName("pagination respects page and size")
        void pagination_respectsPageAndSize() throws Exception {
            mockMvc.perform(get("/api/batches")
                    .param("page", "0")
                    .param("size", "1")
                    .with(curator())
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.size").value(1))
                    .andExpect(jsonPath("$.number").value(0))
                    .andExpect(jsonPath("$.totalElements").value(2));
        }

        @Test
        @DisplayName("returns empty page when no batches")
        void returnsEmptyPage_whenNoBatches() throws Exception {
            batchRepository.deleteAll();

            mockMvc.perform(get("/api/batches")
                    .with(curator())
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content.length()").value(0))
                    .andExpect(jsonPath("$.totalElements").value(0));
        }
    }
}
