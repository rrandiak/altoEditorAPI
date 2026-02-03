package cz.inovatika.altoEditor.domain.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import cz.inovatika.altoEditor.domain.model.User;

/**
 * Spring Data JPA Repository for User entity.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long>,
        JpaSpecificationExecutor<User> {

    /**
     * Find user by username.
     */
    Optional<User> findByUsername(String username);

    /**
     * Check if user exists by username.
     */
    boolean existsByUsername(String username);

    /**
     * Check if engine user exists by username.
     */
    @Query("""
            SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END
            FROM User u
            WHERE u.username = :username AND u.isEngine = true
            """)
    boolean existsEngineByUsername(String username);

    /**
     * Find Kramerius user.
     */
    @Query("""
            SELECT u
            FROM User u
            WHERE u.isKramerius = true AND u.username = :username
            """)
    Optional<User> findKrameriusByUsername(String username);
}
