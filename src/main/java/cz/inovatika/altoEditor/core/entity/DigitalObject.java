package cz.inovatika.altoEditor.core.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

import cz.inovatika.altoEditor.core.enums.DigitalObjectState;

/**
 * DigitalObject entity with JPA annotations.
 */
@Entity
@Table(name = "digital_objects")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DigitalObject {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "digital_objects_id_seq")
    @SequenceGenerator(name = "digital_objects_id_seq", sequenceName = "digital_objects_id_seq", allocationSize = 1)
    @Column(name = "id")
    private Integer id;

    @Column(name = "r_user_id")
    private Integer rUserId;

    @Column(name = "instance")
    private String instance;

    @Column(name = "pid")
    private String pid;

    @Column(name = "label")
    private String label;

    @Column(name = "parent_path")
    private String parentPath;

    @Column(name = "parent_label")
    private String parentLabel;

    @Column(name = "version")
    private String version;

    @Column(name = "date")
    private LocalDateTime date;

    @Column(name = "state")
    private DigitalObjectState state;

    @Column(name = "lock")
    private Boolean lock;

    @PrePersist
    void created() {
        this.date = LocalDateTime.now();
    }

    @PreUpdate
    void updated() {
        this.date = LocalDateTime.now();
    }

    public String getNextVersion() {
        String[] versionParts = version.split("\\.");
        int versionNum = Integer.parseInt(versionParts[1]);

        int newVersionNum = versionNum + 1;
        return versionParts[0] + "." + newVersionNum;
    }
}
