package cz.inovatika.altoEditor.domain.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import cz.inovatika.altoEditor.domain.enums.SpecialUser;
import cz.inovatika.altoEditor.domain.model.User;

/**
 * Spring Data JPA Repository for User entity.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Integer> {

    /**
     * Find user by login.
     */
    Optional<User> findByLogin(String login);

    /**
     * Check if user exists by login.
     */
    boolean existsByLogin(String login);
    
    /**
     * Find a special user by enum.
     */
    @Query("SELECT u FROM User u WHERE u.login = :#{#specialUser.getUsername()}")
    Optional<User> findSpecialUser(SpecialUser specialUser);
}
