package cz.inovatika.altoEditor.presentation.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import cz.inovatika.altoEditor.presentation.facade.AltoVersionFacade;

@SpringBootTest
@AutoConfigureMockMvc
public class AltoVersionControllerTest extends ControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AltoVersionFacade facade;

    private final int TEST_OBJECT_ID = 1;

    @Test
    @WithMockUser(authorities = { "CURATOR" })
    void setObjectActive_shouldReturnOk_whenUserIsCurator() throws Exception {
        mockMvc.perform(post("/api/alto-versions/" + TEST_OBJECT_ID + "/set-active")
                .contentType(MediaType.APPLICATION_JSON)
                .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = { "EDITOR" })
    void setObjectActive_shouldReturnForbidden_whenUserIsEditor() throws Exception {
        mockMvc.perform(post("/api/alto-versions/" + TEST_OBJECT_ID + "/set-active")
                .contentType(MediaType.APPLICATION_JSON)
                .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = { "USER" })
    void setObjectActive_shouldReturnForbidden_whenUserHasNoProperAuthority() throws Exception {
        mockMvc.perform(post("/api/alto-versions/" + TEST_OBJECT_ID + "/set-active")
                .contentType(MediaType.APPLICATION_JSON)
                .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void setObjectActive_shouldReturnUnauthorized_whenNoUser() throws Exception {
        mockMvc.perform(post("/api/alto-versions/" + TEST_OBJECT_ID + "/set-active")
                .contentType(MediaType.APPLICATION_JSON)
                .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isForbidden());
    }
}
