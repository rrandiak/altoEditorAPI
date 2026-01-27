package cz.inovatika.altoEditor.presentation.rest;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import cz.inovatika.altoEditor.application.facade.KrameriusFacade;
import cz.inovatika.altoEditor.infrastructure.kramerius.model.KrameriusObjectMetadata;

@WebMvcTest(KrameriusController.class)
class KrameriusControllerTest extends ControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private KrameriusFacade facade;

    private final String TEST_PID = "uuid:12345678-1234-1234-1234-1234567890ab";
    private KrameriusObjectMetadata mockMetadata;

    @BeforeEach
    void setUp() {
        mockMetadata = KrameriusObjectMetadata.builder()
                .pid(TEST_PID)
                .title("Test Title")
                .parentPath("/parent/path")
                .parentTitle("Parent Title")
                .build();
    }

    @Test
    @WithMockUser(authorities = { "EDITOR" })
    void getKrameriusObject_shouldReturnMetadata_whenUserIsEditor() throws Exception {
        when(facade.getKrameriusObject(ArgumentMatchers.eq(TEST_PID), ArgumentMatchers.any()))
                .thenReturn(mockMetadata);

        mockMvc.perform(get("/api/kramerius/objects/" + TEST_PID)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pid").value(TEST_PID))
                .andExpect(jsonPath("$.title").value("Test Title"));
    }

    @Test
    @WithMockUser(authorities = { "CURATOR" })
    void getKrameriusObject_shouldReturnMetadata_whenUserIsCurator() throws Exception {
        when(facade.getKrameriusObject(ArgumentMatchers.eq(TEST_PID), ArgumentMatchers.any()))
                .thenReturn(mockMetadata);

        mockMvc.perform(get("/api/kramerius/objects/" + TEST_PID)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pid").value(TEST_PID))
                .andExpect(jsonPath("$.title").value("Test Title"));
    }

    @Test
    @WithMockUser(authorities = { "USER" })
    void getKrameriusObject_shouldReturnForbidden_whenUserHasNoProperAuthority() throws Exception {
        mockMvc.perform(get("/api/kramerius/objects/" + TEST_PID)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    void getKrameriusObject_shouldReturnUnauthorized_whenNoUser() throws Exception {
        mockMvc.perform(get("/api/kramerius/objects/" + TEST_PID)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }
}
