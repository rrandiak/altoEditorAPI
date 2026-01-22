package cz.inovatika.altoEditor.core.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * JPA Entity representing a version record.
 */
@Entity
@Table(name = "version")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Version {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

    @Column(name = "version")
    private Integer version;
}