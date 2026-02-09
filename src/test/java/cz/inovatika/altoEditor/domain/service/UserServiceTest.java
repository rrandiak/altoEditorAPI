package cz.inovatika.altoEditor.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import cz.inovatika.altoEditor.domain.model.User;
import cz.inovatika.altoEditor.domain.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository repository;

    @InjectMocks
    private UserService service;

    private static final Long USER_ID = 1L;
    private static final String USERNAME = "editor";
    private static final String UID = "uid-123";
    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(USER_ID)
                .uid(UID)
                .username(USERNAME)
                .build();
    }

    @Nested
    @DisplayName("createUser")
    class CreateUser {

        @Test
        @DisplayName("saves and returns user when username does not exist")
        void savesAndReturnsUser_whenUsernameAvailable() {
            when(repository.existsByUsername(USERNAME)).thenReturn(false);
            when(repository.save(any(User.class))).thenReturn(user);

            User result = service.createUser(UID, USERNAME);

            assertThat(result).isEqualTo(user);
            verify(repository).existsByUsername(USERNAME);
            verify(repository).save(any(User.class));
        }

        @Test
        @DisplayName("throws IllegalArgumentException when username already exists")
        void throws_whenUsernameExists() {
            when(repository.existsByUsername(USERNAME)).thenReturn(true);

            assertThrows(IllegalArgumentException.class, () -> service.createUser(UID, USERNAME));

            verify(repository).existsByUsername(USERNAME);
        }
    }

    @Nested
    @DisplayName("getUserById")
    class GetUserById {

        @Test
        @DisplayName("returns user when found")
        void returnsUser_whenFound() {
            when(repository.findById(USER_ID)).thenReturn(Optional.of(user));

            User result = service.getUserById(USER_ID);

            assertThat(result).isEqualTo(user);
            verify(repository).findById(USER_ID);
        }

        @Test
        @DisplayName("throws IllegalArgumentException when not found")
        void throws_whenNotFound() {
            when(repository.findById(999L)).thenReturn(Optional.empty());

            assertThrows(IllegalArgumentException.class, () -> service.getUserById(999L));

            verify(repository).findById(999L);
        }
    }

    @Nested
    @DisplayName("getUserByUsername")
    class GetUserByUsername {

        @Test
        @DisplayName("returns user when found")
        void returnsUser_whenFound() {
            when(repository.findByUsername(USERNAME)).thenReturn(Optional.of(user));

            User result = service.getUserByUsername(USERNAME);

            assertThat(result).isEqualTo(user);
            verify(repository).findByUsername(USERNAME);
        }

        @Test
        @DisplayName("throws IllegalArgumentException when not found")
        void throws_whenNotFound() {
            when(repository.findByUsername("nonexistent")).thenReturn(Optional.empty());

            assertThrows(IllegalArgumentException.class, () -> service.getUserByUsername("nonexistent"));

            verify(repository).findByUsername("nonexistent");
        }
    }

    @Nested
    @DisplayName("search")
    @SuppressWarnings("unchecked")
    class Search {

        @Test
        @DisplayName("delegates to repository with specification and pageable")
        void delegatesToRepository() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<User> expectedPage = new PageImpl<>(List.of(user));
            when(repository.findAll(isA(Specification.class), eq(pageable))).thenReturn(expectedPage);

            Page<User> result = service.search(true, false, true, pageable);

            assertThat(result).isNotNull();
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0)).isEqualTo(user);
            verify(repository).findAll(isA(Specification.class), eq(pageable));
        }

        @Test
        @DisplayName("returns empty page when no results")
        void returnsEmptyPage_whenNoResults() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<User> emptyPage = new PageImpl<>(List.of(), pageable, 0);
            when(repository.findAll(isA(Specification.class), eq(pageable))).thenReturn(emptyPage);

            Page<User> result = service.search(null, null, null, pageable);

            assertThat(result.getContent()).isEmpty();
            assertThat(result.getTotalElements()).isEqualTo(0);
        }
    }
}
