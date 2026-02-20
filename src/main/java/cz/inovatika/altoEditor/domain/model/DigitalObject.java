package cz.inovatika.altoEditor.domain.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.search.engine.backend.types.Sortable;
import org.hibernate.search.mapper.pojo.bridge.mapping.annotation.RoutingBinderRef;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.DocumentId;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.FullTextField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.GenericField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.IndexingDependency;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.Indexed;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.KeywordField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.ObjectPath;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.PropertyValue;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import cz.inovatika.altoEditor.domain.enums.Model;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
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
@EntityListeners(AuditingEntityListener.class)
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

    /** Sortable copy of title (FullTextField is analyzed and cannot be sorted). */
    @Transient
    @KeywordField(name = "title_sort", sortable = Sortable.YES)
    @IndexingDependency(derivedFrom = @ObjectPath(@PropertyValue(propertyName = "title")))
    public String getTitleSort() {
        return title;
    }

    @Column(columnDefinition = "smallint")
    @GenericField(name = "level", sortable = Sortable.YES)
    private Integer level;

    @Column(columnDefinition = "smallint")
    @GenericField(name = "indexInParent", sortable = Sortable.YES)
    private Integer indexInParent;

    /**
     * Timestamp of last update of this digital object.
     */
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    @GenericField(sortable = Sortable.YES)
    private LocalDateTime updatedAt;

    /** Total descendant pages (excluding this node if it is a page). Persisted and updated on hierarchy/ALTO changes. */
    @Column(name = "pages_count")
    @GenericField(name = "pagesCount", sortable = Sortable.YES)
    private Integer pagesCount;

    /** Descendant pages that have at least one ALTO version. Persisted and updated on hierarchy/ALTO changes. */
    @Column(name = "pages_with_alto")
    @GenericField(name = "pagesWithAlto", sortable = Sortable.YES)
    private Integer pagesWithAlto;

    /** True if this node has at least one child whose model is not {@code page}. Updated when hierarchy is refreshed. */
    @Column(name = "has_subhierarchy", nullable = false)
    @GenericField(name = "hasSubhierarchy")
    @Builder.Default
    private boolean hasSubhierarchy = false;

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

    @KeywordField(name = "pid", sortable = Sortable.YES)
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
