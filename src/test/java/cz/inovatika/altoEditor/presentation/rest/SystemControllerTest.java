package cz.inovatika.altoEditor.presentation.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class SystemControllerTest extends ControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Should return 200 OK and InfoDto for user with EDITOR authority")
    @WithMockUser(authorities = "EDITOR")
    void getInfo_asEditor_returnsOk() throws Exception {
        mockMvc.perform(get("/api/system/info")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test
    @DisplayName("Should return 200 OK and InfoDto for user with CURATOR authority")
    @WithMockUser(authorities = "CURATOR")
    void getInfo_asCurator_returnsOk() throws Exception {
        mockMvc.perform(get("/api/system/info")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test
    @DisplayName("Should return 403 Forbidden for user without required authority")
    @WithMockUser(authorities = "USER")
    void getInfo_asUser_returnsForbidden() throws Exception {
        mockMvc.perform(get("/api/system/info")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Should return 401 Unauthorized for anonymous user")
    void getInfo_anonymous_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/system/info")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }
}