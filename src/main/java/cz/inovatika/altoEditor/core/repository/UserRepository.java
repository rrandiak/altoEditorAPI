package cz.inovatika.altoEditor.core.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import cz.inovatika.altoEditor.core.entity.User;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA Repository for User entity.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Integer> {

    /**
     * Find all users ordered by login ascending.
     */
    List<User> findAllByOrderByLoginAsc();

    /**
     * Find user by login.
     */
    Optional<User> findByLogin(String login);

    /**
     * Check if user exists by login.
     */
    boolean existsByLogin(String login);
}
