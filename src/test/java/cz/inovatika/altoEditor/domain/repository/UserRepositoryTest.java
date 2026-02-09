package cz.inovatika.altoEditor.domain.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.TestPropertySource;

import cz.inovatika.altoEditor.domain.model.User;

@DataJpaTest
@TestPropertySource(properties = "altoeditor.home=src/test/resources")
class UserRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private UserRepository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @Nested
    @DisplayName("save")
    class Save {

        @Test
        @DisplayName("persists user and assigns id")
        void persistsUserAndAssignsId() {
            User user = User.builder().username("testuser").build();

            User saved = repository.save(user);
            entityManager.flush();

            assertThat(saved.getId()).isNotNull();
            assertThat(saved.getUsername()).isEqualTo("testuser");
        }
    }

    @Nested
    @DisplayName("findByUsername")
    class FindByUsername {

        @Test
        @DisplayName("returns user when username exists")
        void returnsUser_whenExists() {
            repository.saveAll(List.of(
                    User.builder().username("user1").build(),
                    User.builder().username("user2").build()));
            entityManager.flush();

            Optional<User> found = repository.findByUsername("user1");

            assertThat(found).isPresent();
            assertThat(found.get().getId()).isPositive();
            assertThat(found.get().getUsername()).isEqualTo("user1");
        }

        @Test
        @DisplayName("returns empty when username does not exist")
        void returnsEmpty_whenNotExists() {
            Optional<User> found = repository.findByUsername("nonexistent");

            assertThat(found).isEmpty();
        }
    }

    @Nested
    @DisplayName("existsByUsername")
    class ExistsByUsername {

        @Test
        @DisplayName("returns true when user exists")
        void returnsTrue_whenExists() {
            repository.save(User.builder().username("existing").build());
            entityManager.flush();

            boolean exists = repository.existsByUsername("existing");

            assertThat(exists).isTrue();
        }

        @Test
        @DisplayName("returns false when user does not exist")
        void returnsFalse_whenNotExists() {
            boolean exists = repository.existsByUsername("nonexistent");

            assertThat(exists).isFalse();
        }
    }

    @Nested
    @DisplayName("existsEngineByUsername")
    class ExistsEngineByUsername {

        @Test
        @DisplayName("returns true when user exists and isEngine is true")
        void returnsTrue_whenUserIsEngine() {
            repository.save(User.builder().username("tesseract").engine(true).build());
            entityManager.flush();

            boolean exists = repository.existsEngineByUsername("tesseract");

            assertThat(exists).isTrue();
        }

        @Test
        @DisplayName("returns false when user exists but isEngine is false")
        void returnsFalse_whenUserIsNotEngine() {
            repository.save(User.builder().username("editor").engine(false).build());
            entityManager.flush();

            boolean exists = repository.existsEngineByUsername("editor");

            assertThat(exists).isFalse();
        }

        @Test
        @DisplayName("returns false when username does not exist")
        void returnsFalse_whenUserNotExists() {
            boolean exists = repository.existsEngineByUsername("nonexistent");

            assertThat(exists).isFalse();
        }
    }
}
