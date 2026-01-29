package cz.inovatika.altoEditor.presentation.rest;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import cz.inovatika.altoEditor.presentation.dto.response.BatchDto;
import cz.inovatika.altoEditor.presentation.facade.BatchFacade;
import cz.inovatika.altoEditor.presentation.dto.request.BatchSearchRequest;

@WebMvcTest(BatchController.class)
class BatchControllerTest extends ControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BatchFacade facade;

    private Page<BatchDto> batchDtoPage;

    @BeforeEach
    void setUp() {
        BatchDto dto = BatchDto.builder()
                .id(1)
                .pid("pid1")
                .instance("inst1")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .objectId(100)
                .estimatedItemCount(10)
                .log("log")
                .build();
        batchDtoPage = new PageImpl<>(Collections.singletonList(dto), PageRequest.of(0, 10), 1);
    }

    @Test
    @WithMockUser(authorities = { "CURATOR" })
    void getBatches_shouldReturnPage_whenUserIsCurator() throws Exception {
        when(facade.searchBatches(ArgumentMatchers.any(BatchSearchRequest.class),
                ArgumentMatchers.any(Pageable.class)))
                .thenReturn(batchDtoPage);

        mockMvc.perform(get("/api/batches")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(1));
    }

    @Test
    @WithMockUser(authorities = { "EDITOR" })
    void getBatches_shouldReturnForbidden_whenUserIsEditor() throws Exception {
        mockMvc.perform(get("/api/batches")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = { "USER" })
    void getBatches_shouldReturnForbidden_whenUserHasNoProperAuthority() throws Exception {
        mockMvc.perform(get("/api/batches")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    void getBatches_shouldReturnUnauthorized_whenNoUser() throws Exception {
        mockMvc.perform(get("/api/batches")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }
}