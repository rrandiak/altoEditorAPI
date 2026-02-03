package cz.inovatika.altoEditor.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import cz.inovatika.altoEditor.domain.model.AltoVersion;
import jakarta.transaction.Transactional;

@Repository
public interface AltoVersionRepository
        extends JpaRepository<AltoVersion, Integer>,
        JpaSpecificationExecutor<AltoVersion> {

    /**
     * Find all digital objects by UUID
     */
    List<AltoVersion> findAllByDigitalObjectUuid(UUID uuid);

    /**
     * Check if any digital object exists with the given UUID
     */
    boolean existsByDigitalObjectUuid(UUID uuid);

    /**
     * Find active digital objects by UUID
     */
    @Query("""
                SELECT d
                FROM AltoVersion d
                WHERE d.digitalObject.uuid = :uuid
                AND d.state = AltoVersionState.ACTIVE
            """)
    Optional<AltoVersion> findActive(@Param("uuid") UUID uuid);

    /**
     * Find digital object by UUID and user ID in PENDING state
     */
    @Query("""
                SELECT d
                FROM AltoVersion d
                WHERE d.digitalObject.uuid = :uuid
                AND d.user.id = :userId
                AND d.state = AltoVersionState.PENDING
            """)
    Optional<AltoVersion> findPendingForUser(UUID uuid, Long userId);

    @Query("""
                SELECT d
                FROM AltoVersion d
                WHERE d.digitalObject.uuid = :uuid
                AND d.user.id = :userId
                AND d.state = AltoVersionState.ACTIVE
            """)
    Optional<AltoVersion> findActiveForUser(UUID uuid, Long userId);

    @Query("""
                SELECT d
                FROM AltoVersion d
                WHERE d.digitalObject.uuid = :uuid
                AND d.user.id = :userId
                AND d.state = AltoVersionState.STALE
            """)
    Optional<AltoVersion> findStaleForUser(UUID uuid, Long userId);

    @Query("""
                SELECT d
                FROM AltoVersion d
                WHERE d.digitalObject.uuid = :uuid
                AND d.user.id = :userId
                AND d.contentHash = :contentHash
                AND d.state in (AltoVersionState.ACTIVE, AltoVersionState.PENDING, AltoVersionState.ARCHIVED)
            """)
    Optional<AltoVersion> findEngineUpdateCandidate(UUID uuid, Long userId, String contentHash);

    /**
     * Find digital object by UUID and version
     */
    Optional<AltoVersion> findByDigitalObjectUuidAndVersion(UUID uuid, Integer version);

    /**
     * Find digital object by UUID and instance ID
     */
    Optional<AltoVersion> findFirstByDigitalObjectUuidAndInstance(UUID uuid, String instance);

    /**
     * Find the digital object with the highest version for the given UUID
     */
    Optional<AltoVersion> findFirstByDigitalObjectUuidOrderByVersionDesc(UUID uuid);

    /**
     * Find digital object by UUID with priority ordering based on version and user
     * type.
     * The digital object is retrieved in the following order:
     * 1. The version owned by the current user.
     * 2. The version currently in 'ACTIVE' state.
     */
    @Query("""
                SELECT d
                FROM AltoVersion d
                WHERE d.digitalObject.uuid = :uuid
                    AND (d.user.id = :userId
                        OR d.state = AltoVersionState.ACTIVE)
                ORDER BY CASE
                    WHEN d.user.id = :userId THEN 0
                    ELSE 1
                END
                LIMIT 1
            """)
    Optional<AltoVersion> findRelated(
            @Param("uuid") UUID uuid,
            @Param("userId") Long userId);

    @Modifying
    @Transactional
    @Query(value = """
            DELETE FROM alto_version_present_in_instances
            WHERE alto_version_id IN
                (SELECT av.id FROM alto_versions av WHERE av.uuid = :uuid)
                AND instance = :instance
            """, nativeQuery = true)
    void removeInstanceAssociation(@Param("uuid") UUID uuid, @Param("instance") String instance);
}