package cz.inovatika.altoEditor.presentation.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import cz.inovatika.altoEditor.presentation.dto.response.UserDto;
import cz.inovatika.altoEditor.presentation.facade.UserFacade;

@SpringBootTest
class UserControllerTest extends ControllerTest {

    @MockitoBean
    private UserFacade facade;

    private Page<UserDto> userDtoPage;

    @BeforeEach
    void setUp() {
        userDtoPage = new PageImpl<>(List.of(
                UserDto.builder().id(1).username("user1").build(),
                UserDto.builder().id(2).username("user2").build(),
                UserDto.builder().id(3).username("user3").build()), PageRequest.of(0, 10), 1);
    }

    @Test
    void getUsers_returnsPagedUsers_unit() {
        UserController controller = new UserController(facade);
        when(facade.searchUsers(org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(userDtoPage);

        ResponseEntity<Page<UserDto>> response = controller.getUsers(PageRequest.of(0, 10));

        assertNotNull(response);
        assertEquals(3, response.getBody().getContent().size());
        verify(facade, times(1)).searchUsers(org.mockito.ArgumentMatchers.any(Pageable.class));
    }
}