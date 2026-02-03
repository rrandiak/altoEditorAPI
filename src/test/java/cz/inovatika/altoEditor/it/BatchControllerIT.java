package cz.inovatika.altoEditor.it;

import cz.inovatika.altoEditor.config.properties.AuthProperties;
import cz.inovatika.altoEditor.domain.enums.BatchPriority;
import cz.inovatika.altoEditor.domain.enums.BatchState;
import cz.inovatika.altoEditor.domain.enums.BatchSubstate;
import cz.inovatika.altoEditor.domain.enums.BatchType;
import cz.inovatika.altoEditor.domain.model.Batch;
import cz.inovatika.altoEditor.domain.repository.BatchRepository;
import cz.inovatika.altoEditor.infrastructure.kramerius.adapter.k7.model.K7UserResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.http.HttpMethod;
import org.mockito.Mockito;
import org.springframework.web.client.RestTemplate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = "altoeditor.home=src/test/resources")
public class BatchControllerIT {

    @MockitoBean
    private RestTemplate restTemplate;

    @Autowired
    private AuthProperties authConfig;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BatchRepository batchRepository;

    @TempDir
    static Path storeDir;

    @DynamicPropertySource
    static void registerTempDirs(DynamicPropertyRegistry registry) {
        registry.add("application.store.path", () -> storeDir.toString());
    }

    @BeforeEach
    void setupRestTemplateMock() {
        // Prepare mock user response
        K7UserResponse mockUser = new K7UserResponse();
        mockUser.setUid("testuser");
        mockUser.setRoles(List.of("AltoCurator"));

        String userInfoUrl = authConfig.getUserInfoUrl();

        Mockito.when(restTemplate.exchange(
                Mockito.eq(userInfoUrl),
                Mockito.eq(HttpMethod.GET),
                Mockito.any(),
                Mockito.eq(K7UserResponse.class)
        )).thenReturn(new ResponseEntity<>(mockUser, HttpStatus.OK));
    }

    @BeforeEach
    void setUpData() {
        batchRepository.deleteAll();
        Batch batch1 = new Batch();
        batch1.setPid("PID-001");
        batch1.setState(BatchState.PLANNED);
        batch1.setSubstate(BatchSubstate.DOWNLOADING);
        batch1.setPriority(BatchPriority.MEDIUM);
        batch1.setType(BatchType.GENERATE_SINGLE);
        batch1.setInstance("instance1");
        batch1.setObjectId(100);
        batch1.setEstimatedItemCount(10);
        batch1.setLog("Test log 1");
        batch1.setCreatedAt(LocalDateTime.now().minusDays(1));
        batch1.setUpdatedAt(LocalDateTime.now());

        Batch batch2 = new Batch();
        batch2.setPid("PID-002");
        batch2.setState(BatchState.RUNNING);
        batch2.setSubstate(BatchSubstate.GENERATING);
        batch2.setPriority(BatchPriority.HIGH);
        batch2.setType(BatchType.RETRIEVE_HIERARCHY);
        batch2.setInstance("instance2");
        batch2.setObjectId(200);
        batch2.setEstimatedItemCount(20);
        batch2.setLog("Test log 2");
        batch2.setCreatedAt(LocalDateTime.now().minusDays(2));
        batch2.setUpdatedAt(LocalDateTime.now());

        batchRepository.save(batch1);
        batchRepository.save(batch2);
    }

    @Test
    void getBatches_returnsAllBatches() throws Exception {
        mockMvc.perform(get("/api/batches")
                .with(user("testuser").authorities(new SimpleGrantedAuthority("CURATOR")))
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].pid").value("PID-001"))
                .andExpect(jsonPath("$.content[1].pid").value("PID-002"));
    }

    @Test
    void getBatches_filterByState_returnsOnlyMatching() throws Exception {
        mockMvc.perform(get("/api/batches")
                .param("state", "PLANNED")
                .with(user("testuser").authorities(new SimpleGrantedAuthority("CURATOR")))
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].pid").value("PID-001"));
    }

    @Test
    void getBatches_filterByPriority_returnsOnlyMatching() throws Exception {
        mockMvc.perform(get("/api/batches")
                .param("priority", "HIGH")
                .with(user("testuser").authorities(new SimpleGrantedAuthority("CURATOR")))
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].pid").value("PID-002"));
    }

    @Test
    void getBatches_filterByType_returnsOnlyMatching() throws Exception {
        mockMvc.perform(get("/api/batches")
                .param("type", "GENERATE")
                .with(user("testuser").authorities(new SimpleGrantedAuthority("CURATOR")))
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].pid").value("PID-001"));
    }
}
