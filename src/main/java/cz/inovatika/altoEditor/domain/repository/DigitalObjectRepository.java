package cz.inovatika.altoEditor.domain.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import cz.inovatika.altoEditor.domain.model.DigitalObject;
import cz.inovatika.altoEditor.domain.model.dto.PageCountStats;
import jakarta.transaction.Transactional;

@Repository
public interface DigitalObjectRepository
        extends JpaRepository<DigitalObject, UUID>,
        JpaSpecificationExecutor<DigitalObject> {

    /**
     * Counts total number of descendant pages and number of those with ALTO versions.
     * Uses a recursive CTE for hierarchy traversal (no Java recursion, no DB columns).
     * Aliases match PageCountStats getters (getTotalPages, getPagesWithAlto).
     *
     * @param uuid UUID of the digital object
     */
    @Query(value = """
            WITH RECURSIVE descendants (uuid, parent_uuid, model) AS (
                SELECT uuid, parent_uuid, model
                FROM object_hierarchy
                WHERE uuid = :uuid
                UNION ALL
                SELECT oh.uuid, oh.parent_uuid, oh.model
                FROM object_hierarchy oh
                INNER JOIN descendants d ON oh.parent_uuid = d.uuid
            )
            SELECT
                COUNT(DISTINCT CASE WHEN d.model = 'page' AND d.uuid != :uuid THEN d.uuid END) as "totalPages",
                COUNT(DISTINCT CASE WHEN d.model = 'page' AND d.uuid != :uuid AND EXISTS (
                    SELECT 1 FROM alto_versions av2 WHERE av2.uuid = d.uuid
                ) THEN d.uuid END) as "pagesWithAlto"
            FROM descendants d
            """, nativeQuery = true)
    PageCountStats getDescendantPageStats(@Param("uuid") UUID uuid);

    /**
     * Returns PIDs of all descendant pages under the given ancestor (recursive).
     * Includes pages that have no ALTO version yet.
     */
    @Query(value = """
            WITH RECURSIVE descendants (uuid, parent_uuid, model) AS (
                SELECT uuid, parent_uuid, model
                FROM object_hierarchy
                WHERE uuid = :uuid
                UNION ALL
                SELECT oh.uuid, oh.parent_uuid, oh.model
                FROM object_hierarchy oh
                INNER JOIN descendants d ON oh.parent_uuid = d.uuid
            )
            SELECT uuid FROM descendants WHERE model = 'page'
            """, nativeQuery = true)
    List<UUID> findDescendantPageUuids(@Param("uuid") UUID uuid);

    /**
     * Returns descendant node UUIDs in the subtree rooted at the given UUID, including the root,
     * ordered from deepest level to the root so parents can be recomputed from children.
     */
    @Query(value = """
            WITH RECURSIVE descendants (uuid, parent_uuid, level) AS (
                SELECT uuid, parent_uuid, level
                FROM object_hierarchy
                WHERE uuid = :uuid
                UNION ALL
                SELECT oh.uuid, oh.parent_uuid, oh.level
                FROM object_hierarchy oh
                INNER JOIN descendants d ON oh.parent_uuid = d.uuid
            )
            SELECT uuid
            FROM descendants
            ORDER BY level DESC
            """, nativeQuery = true)
    List<UUID> findDescendantUuidsOrderByLevelDesc(@Param("uuid") UUID uuid);

    /**
     * Returns direct children of the given parent UUID.
     */
    List<DigitalObject> findByParentUuid(UUID uuid);

    /**
     * Returns direct child page UUIDs of the given parent that have at least one ALTO version.
     */
    @Query(value = """
            SELECT DISTINCT oh.uuid
            FROM object_hierarchy oh
            INNER JOIN alto_versions av ON av.uuid = oh.uuid
            WHERE oh.parent_uuid = :uuid
              AND oh.model = 'page'
            """, nativeQuery = true)
    List<UUID> findDirectChildPageUuidsWithAlto(@Param("uuid") UUID uuid);

    /**
     * Returns true if the given node has at least one direct child whose model is not {@code page}.
     */
    @Query("SELECT COUNT(c) > 0 FROM DigitalObject c WHERE c.parent.uuid = :uuid AND c.model <> 'page'")
    boolean existsNonPageChild(@Param("uuid") UUID uuid);

    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO object_hierarchy (
                uuid,
                parent_uuid,
                model,
                title,
                level,
                index_in_parent,
                updated_at,
                pages_count,
                pages_with_alto,
                has_subhierarchy
            ) VALUES (
                :uuid,
                :parentUuid,
                :model,
                :title,
                :level,
                :indexInParent,
                CURRENT_TIMESTAMP,
                :pagesCount,
                :pagesWithAlto,
                :hasSubhierarchy
            )
            ON CONFLICT (uuid) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("uuid") UUID uuid,
            @Param("parentUuid") UUID parentUuid,
            @Param("model") String model,
            @Param("title") String title,
            @Param("level") Integer level,
            @Param("indexInParent") Integer indexInParent,
            @Param("pagesCount") Integer pagesCount,
            @Param("pagesWithAlto") Integer pagesWithAlto,
            @Param("hasSubhierarchy") boolean hasSubhierarchy);
}
