package cz.inovatika.altoEditor.domain.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.search.mapper.pojo.bridge.mapping.annotation.RoutingBinderRef;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.DocumentId;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.FullTextField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.GenericField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.IndexingDependency;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.Indexed;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.KeywordField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.ObjectPath;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.PropertyValue;

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
import lombok.ToString;

@Entity
@Table(name = "object_hierarchy", indexes = {
        @Index(columnList = "uuid"),
        @Index(columnList = "parent_uuid")
})
@Data
@ToString(exclude = { "children" })
@Builder(builderClassName = "DigitalObjectBuilder", toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Indexed(routingBinder = @RoutingBinderRef(type = DigitalObjectRoutingBinder.class))
public class DigitalObject {

    @Id
    @Column(columnDefinition = "uuid")
    @DocumentId
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
    @KeywordField(name = "model")
    private String model;

    @Column(length = 255)
    @FullTextField(name = "title")
    private String title;

    @Column(columnDefinition = "smallint")
    @GenericField(name = "level")
    private Integer level;

    @Column(columnDefinition = "smallint")
    @GenericField(name = "indexInParent")
    private Integer indexInParent;

    /** Total descendant pages (excluding this node if it is a page). Persisted and updated on hierarchy/ALTO changes. */
    @Column(name = "pages_count")
    @GenericField(name = "pagesCount")
    private Integer pagesCount;

    /** Descendant pages that have at least one ALTO version. Persisted and updated on hierarchy/ALTO changes. */
    @Column(name = "pages_with_alto")
    @GenericField(name = "pagesWithAlto")
    private Integer pagesWithAlto;

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

    @KeywordField(name = "pid")
    @IndexingDependency(derivedFrom = @ObjectPath(@PropertyValue(propertyName = "uuid")))
    public String getPid() {
        return "uuid:" + this.getUuid().toString();
    }

    @KeywordField(name = "parentPid")
    @IndexingDependency(derivedFrom = @ObjectPath(@PropertyValue(propertyName = "parent")))
    public String getParentPid() {
        return this.parent != null ? this.parent.getPid() : null;
    }

    public DigitalObject getRoot() {
        DigitalObject current = this;
        while (current.getParent() != null) {
            current = current.getParent();
        }
        return current;
    }

    @KeywordField(name = "rootPid")
    @IndexingDependency(derivedFrom = @ObjectPath(@PropertyValue(propertyName = "parent")))
    public String getRootPid() {
        return this.getRoot().getPid();
    }

    public boolean isPage() {
        return this.model.equals(Model.PAGE.toString());
    }

    public boolean hasAltoVersions() {
        return this.altoVersions != null && !this.altoVersions.isEmpty();
    }
}
