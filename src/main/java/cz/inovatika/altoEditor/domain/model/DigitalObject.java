package cz.inovatika.altoEditor.domain.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import cz.inovatika.altoEditor.domain.enums.Model;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "object_hierarchy", indexes = {
        @Index(columnList = "uuid"),
        @Index(columnList = "parent_uuid")
})
@Data
@Builder(builderClassName = "DigitalObjectBuilder", toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class DigitalObject {
    @Id
    @Column(columnDefinition = "uuid")
    private UUID uuid;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "parent_uuid", 
        foreignKey = @ForeignKey(name = "fk_parent_uuid")
    )
    private DigitalObject parent;
    
    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL)
    @Builder.Default
    private List<DigitalObject> children = new ArrayList<>();

    @Column(name = "model", length = 31)
    private String model;

    @Column(length = 255)
    private String title;

    @Column(columnDefinition = "smallint")
    private Integer level;

    @Column(columnDefinition = "smallint")
    private Integer indexInParent;

    @OneToMany(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @JoinColumn(name = "alto_version_id")
    private List<AltoVersion> altoVersions;

    public static class DigitalObjectBuilder {
        public DigitalObjectBuilder pid(String pid) {
            if (!pid.startsWith("uuid:")) {
                throw new IllegalArgumentException("PID must start with 'uuid:'");
            }

            this.uuid = UUID.fromString(pid.substring(5));

            return this;
        }

        public DigitalObjectBuilder title(String title) {
            if (title != null && title.length() > 255) {
                this.title = title.substring(0, 252) + "...";
            } else {
                this.title = title;
            }
            return this;
        }
    }

    public String getPid() {
        return "uuid:" + this.getUuid().toString();
    }

    public DigitalObject getRoot() {
        DigitalObject current = this;
        while (current.getParent() != null) {
            current = current.getParent();
        }
        return current;
    }

    public boolean isPage() {
        return this.model.equals(Model.PAGE.toString());
    }

    public boolean hasAltoVersions() {
        return this.altoVersions != null && !this.altoVersions.isEmpty();
    }
}
