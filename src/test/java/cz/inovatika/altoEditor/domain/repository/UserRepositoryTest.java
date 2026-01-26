package cz.inovatika.altoEditor.domain.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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

    @Test
    @DisplayName("Find by login should return user when exists")
    void findByLogin_shouldReturnUser_whenExists() {    
        // Given
        User user1 = User.builder().login("testuser").build();
        User user2 = User.builder().login("anotheruser").build();
        repository.saveAll(List.of(user1, user2));
        entityManager.flush();

        // When
        Optional<User> found = repository.findByLogin("testuser");

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(1);
    }

    @Test
    @DisplayName("Find by login should return empty when user does not exist")
    void findByLogin_shouldReturnEmpty_whenNotExists() {
        // When
        Optional<User> found = repository.findByLogin("nonexistent");

        // Then
        assertThat(found).isNotPresent();
    }

    @Test
    @DisplayName("Exists by login should return true when user exists")
    void existsByLogin_shouldReturnTrue_whenExists() {
        // Given
        User user = User.builder().login("existinguser").build();
        repository.save(user);
        entityManager.flush();

        // When
        boolean exists = repository.existsByLogin("existinguser");

        // Then
        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("Exists by login should return false when user does not exist")
    void existsByLogin_shouldReturnFalse_whenNotExists() {
        // When
        boolean exists = repository.existsByLogin("nonexistentuser");

        // Then
        assertThat(exists).isFalse();
    }
}