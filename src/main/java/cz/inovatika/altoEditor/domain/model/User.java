package cz.inovatika.altoEditor.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * User entity with JPA annotations.
 */
@Entity
@Table(name = "users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    /**
     * Primary key identifier.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Unique identifier for the user from an external authentication/authorization system.
     */
    @Column(name = "uid")
    private String uid;

    /**
     * Visible username of the user.
     */
    @Column(name = "username", nullable = false, unique = true)
    private String username;
    
    /**
     * True if this user represents an ALTO OCR engine/generator.
     */
    @Column(name = "is_engine", nullable = false)
    @Builder.Default
    private boolean isEngine = false;
}
