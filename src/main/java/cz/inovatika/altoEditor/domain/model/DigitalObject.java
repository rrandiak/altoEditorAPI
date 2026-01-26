package cz.inovatika.altoEditor.domain.model;

import java.time.LocalDateTime;

import cz.inovatika.altoEditor.domain.enums.DigitalObjectState;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DigitalObject entity with JPA annotations.
 */
@Entity
@Table(
    name = "digital_objects",
    uniqueConstraints = @UniqueConstraint(
        columnNames = {"instance_id", "pid", "version"}
    )
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DigitalObject {

    /**
     * Primary key identifier.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "digital_objects_id_seq")
    @SequenceGenerator(name = "digital_objects_id_seq", sequenceName = "digital_objects_id_seq", allocationSize = 1)
    @Column(name = "id")
    private Integer id;

    /**
     * * Owner user of this digital object.
     */
    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    /**
     * Kramerius instance ID.
     */
    @Column(name = "instance_id")
    private String instanceId;

    /**
     * Target PID of the digital object in Kramerius.
     */
    @Column(name = "pid")
    private String pid;

    /**
     * Title of this digital object - page, so the page title / number.
     */
    @Column(name = "label")
    private String label;

    /**
     * Title identifying the context of this digital object.
     * In many cases, this is the title of the parent document.
     * But in deeper hierarchies, like periodicals, it should contain
     * the title of periodical, volume and issue.
     */
    @Column(name = "title")
    private String title;

    /**
     * Version number of this digital object.
     */
    @Column(name = "version", nullable = false)
    private Integer version;

    /**
     * Date of the last change of this digital object.
     */
    @Column(name = "date")
    private LocalDateTime date;

    /**
     * State of this digital object.
     */
    @Column(name = "state")
    private DigitalObjectState state;

    @PrePersist
    void created() {
        this.date = LocalDateTime.now();
    }

    @PreUpdate
    void updated() {
        this.date = LocalDateTime.now();
    }
}
