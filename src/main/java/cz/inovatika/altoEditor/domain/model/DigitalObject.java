package cz.inovatika.altoEditor.domain.model;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

import cz.inovatika.altoEditor.domain.enums.DigitalObjectState;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Index;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DigitalObject entity with JPA annotations.
 */
@Entity
@Table(name = "digital_objects", uniqueConstraints = @UniqueConstraint(columnNames = { "instance_id", "pid",
        "version" }), indexes = {
                @Index(columnList = "pid"),
                @Index(columnList = "user_id")
        })
@Data
@Builder(builderClassName = "DigitalObjectBuilder", toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class DigitalObject {

    /**
     * Primary key identifier.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Target UUID of the digital object in Kramerius.
     */
    @ManyToOne
    @JoinColumn(name = "uuid", referencedColumnName = "uuid", nullable = false)
    private ObjectHierarchyNode hierarchyNode;

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
     * Title of this digital object - page, so the page title / number.
     */
    @Column(name = "title")
    private String title;

    /**
     * Title identifying the context of this digital object.
     */
    @Column(name = "context_title")
    private String contextTitle;

    /**
     * Version number of this digital object.
     */
    @Column(name = "version", nullable = false)
    private Integer version;

    /**
     * Timestamp of creation of this digital object.
     */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Timestamp of last update of this digital object.
     */
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * State of this digital object.
     */
    @Column(name = "state")
    private DigitalObjectState state;

    public static class DigitalObjectBuilder {
        public DigitalObjectBuilder pid(String pid) {
            if (pid.startsWith("uuid:")) {
                this.hierarchyNode = ObjectHierarchyNode.builder()
                        .uuid(UUID.fromString(pid.substring(5)))
                        .build();
            } else {
                this.hierarchyNode = ObjectHierarchyNode.builder()
                        .uuid(UUID.fromString(pid))
                        .build();
            }
            return this;
        }
    }

    public String getPid() {
        return "uuid:" + this.hierarchyNode.getUuid().toString();
    }
}
