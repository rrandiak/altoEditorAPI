package cz.inovatika.altoEditor.domain.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.search.mapper.pojo.automaticindexing.ReindexOnUpdate;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.DocumentId;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.FullTextField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.GenericField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.Indexed;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.IndexingDependency;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.KeywordField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.ObjectPath;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.PropertyValue;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import cz.inovatika.altoEditor.domain.enums.AltoVersionState;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AltoVersion entity with JPA annotations.
 */
@Entity
@Table(name = "alto_versions", uniqueConstraints = @UniqueConstraint(columnNames = { "uuid",
        "version" }), indexes = {
                @Index(columnList = "uuid"),
                @Index(columnList = "user_id")
        })
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder(builderClassName = "AltoVersionBuilder", toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Indexed
public class AltoVersion {

    /**
     * Primary key identifier.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @DocumentId
    private Long id;

    /**
     * Target UUID of the digital object in Kramerius.
     */
    @ManyToOne
    @JoinColumn(name = "uuid", referencedColumnName = "uuid", nullable = false)
    private DigitalObject digitalObject;

    /**
     * Version number of this digital object.
     */
    @Column(name = "version", nullable = false)
    @GenericField
    private Integer version;

    /**
     * * Owner user of this digital object.
     */
    @ManyToOne
    @JoinColumn(name = "user_id", referencedColumnName = "id", nullable = false)
    private User user;

    /**
     * Kramerius instance ID.
     */
    @Column(name = "instance", length = 31, nullable = false)
    @KeywordField
    private String instance;

    /**
     * State of this digital object.
     */
    @Column(name = "state", nullable = false)
    @KeywordField
    private AltoVersionState state;

    /**
     * Timestamp of creation of this digital object.
     */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    @GenericField
    private LocalDateTime createdAt;

    /**
     * Timestamp of last update of this digital object.
     */
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    @GenericField
    private LocalDateTime updatedAt;

    // --- Transient fields for search index ---
    @Transient
    @KeywordField(name = "pid")
    @IndexingDependency(reindexOnUpdate = ReindexOnUpdate.SHALLOW, derivedFrom = {
            @ObjectPath({
                    @PropertyValue(propertyName = "digitalObject"),
                    @PropertyValue(propertyName = "pid")
            })
    })
    public String getPid() {
        return digitalObject != null ? digitalObject.getPid() : null;
    }

    @Transient
    @KeywordField(name = "username")
    @IndexingDependency(reindexOnUpdate = ReindexOnUpdate.SHALLOW, derivedFrom = {
            @ObjectPath({
                    @PropertyValue(propertyName = "user"),
                    @PropertyValue(propertyName = "username")
            })
    })
    public String getUsername() {
        return user != null ? user.getUsername() : null;
    }

    @Transient
    @FullTextField(analyzer = "standard", name = "pageTitle")
    @IndexingDependency(reindexOnUpdate = ReindexOnUpdate.SHALLOW, derivedFrom = {
            @ObjectPath({
                    @PropertyValue(propertyName = "digitalObject"),
                    @PropertyValue(propertyName = "title")
            })
    })
    public String getPageTitle() {
        return digitalObject != null ? digitalObject.getTitle() : null;
    }

    @Transient
    @GenericField(name = "pageIndex")
    @IndexingDependency(reindexOnUpdate = ReindexOnUpdate.SHALLOW, derivedFrom = {
            @ObjectPath({
                    @PropertyValue(propertyName = "digitalObject"),
                    @PropertyValue(propertyName = "indexInParent")
            })
    })
    public Integer getPageIndex() {
        return digitalObject != null ? digitalObject.getIndexInParent() : null;
    }

    @Transient
    @KeywordField(name = "ancestorPids")
    @IndexingDependency(reindexOnUpdate = ReindexOnUpdate.SHALLOW, derivedFrom = {
            @ObjectPath({
                    @PropertyValue(propertyName = "digitalObject"),
                    @PropertyValue(propertyName = "parent"),
                    @PropertyValue(propertyName = "uuid")
            })
    })
    public List<String> getAncestorPids() {
        if (digitalObject == null) {
            return new ArrayList<>();
        }

        List<String> ancestorPids = new ArrayList<>();

        DigitalObject current = digitalObject.getParent();
        while (current != null) {
            ancestorPids.add(current.getUuid().toString());
            current = current.getParent();
        }

        return ancestorPids;
    }

    @Transient
    @FullTextField(analyzer = "standard", name = "ancestorTitles")
    @IndexingDependency(reindexOnUpdate = ReindexOnUpdate.SHALLOW, derivedFrom = {
            @ObjectPath({
                    @PropertyValue(propertyName = "digitalObject"),
                    @PropertyValue(propertyName = "parent"),
                    @PropertyValue(propertyName = "title")
            })
    })
    public List<String> getAncestorTitles() {
        if (digitalObject == null) {
            return new ArrayList<>();
        }

        List<String> ancestorTitles = new ArrayList<>();

        DigitalObject current = digitalObject.getParent();
        while (current != null) {
            ancestorTitles.add(current.getTitle());
            current = current.getParent();
        }

        return ancestorTitles;
    }
}
