package cz.inovatika.altoEditor.domain.repository;

import java.util.List;
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
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Find user by username.
     */
    Optional<User> findByUsername(String username);

    /**
     * Check if user exists by username.
     */
    boolean existsByUsername(String username);
    
    /**
     * Find a special user by enum.
     */
    @Query("SELECT u FROM User u WHERE u.username = :#{#specialUser.getUsername()}")
    Optional<User> findSpecialUser(SpecialUser specialUser);

    @Query("SELECT u FROM User u WHERE u.isEngine = true")
    List<User> findAllIsEngine();
}
