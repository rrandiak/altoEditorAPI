package cz.inovatika.altoEditor.presentation.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import cz.inovatika.altoEditor.application.facade.UserFacade;
import cz.inovatika.altoEditor.presentation.dto.response.UserDto;

@WebMvcTest(UserController.class)
class UserControllerTest extends ControllerTest {

    @MockitoBean
    private UserFacade facade;

    private Page<UserDto> userDtoPage;

    @BeforeEach
    void setUp() {
        userDtoPage = new PageImpl<>(List.of(
                UserDto.builder().id(1).login("user1").build(),
                UserDto.builder().id(2).login("user2").build(),
                UserDto.builder().id(3).login("user3").build()), PageRequest.of(0, 10), 1);
    }

    @Test
    @WithMockUser(authorities = { "EDITOR" })
    void getUsers_returnsPagedUsers() {
        when(facade.searchUsers(org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(userDtoPage);

        UserController controller = new UserController(facade);
        ResponseEntity<Page<UserDto>> response = controller.getUsers(PageRequest.of(0, 10));

        assertNotNull(response);
        assertEquals(3, response.getBody().getContent().size());
        verify(facade, times(1)).searchUsers(org.mockito.ArgumentMatchers.any(Pageable.class));
    }
}