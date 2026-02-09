package cz.inovatika.altoEditor.domain.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import cz.inovatika.altoEditor.domain.model.DigitalObject;
import cz.inovatika.altoEditor.domain.model.dto.PageCountStats;

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
                COUNT(CASE WHEN d.model = 'page' AND d.uuid != :uuid THEN 1 END) as "totalPages",
                COUNT(DISTINCT CASE WHEN d.model = 'page' AND d.uuid != :uuid AND av.uuid IS NOT NULL THEN d.uuid END) as "pagesWithAlto"
            FROM descendants d
            LEFT JOIN alto_versions av ON av.uuid = d.uuid
            """, nativeQuery = true)
    PageCountStats getDescendantPageStats(@Param("uuid") UUID uuid);
}
