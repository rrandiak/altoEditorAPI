package cz.inovatika.altoEditor.domain.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import cz.inovatika.altoEditor.domain.model.DigitalObject;

@Repository
public interface DigitalObjectRepository
        extends JpaRepository<DigitalObject, Integer>,
        JpaSpecificationExecutor<DigitalObject> {

    /**
     * Find all digital objects by PID
     */
    List<DigitalObject> findByPid(String pid);

    /**
     * Check if any digital object exists with the given PID
     */
    boolean existsByPid(String pid);

    /**
     * Find digital object by PID and user ID
     */
    Optional<DigitalObject> findByPidAndUserId(String pid, Integer userId);

    /**
     * Find all digital objects by PID and user ID
     */
    List<DigitalObject> findAllByPidAndUserId(String pid, Integer userId);

    /**
     * Find digital object by PID and instance ID
     */
    Optional<DigitalObject> findByPidAndInstanceId(String pid, String instanceId);

    /**
     * Find digital object by PID, user ID, and version
     */
    Optional<DigitalObject> findByPidAndUserIdAndVersion(String pid, Integer userId, Integer version);

    /**
     * Find the digital object with the highest version for the given PID
     */
    Optional<DigitalObject> findFirstByPidOrderByVersionDesc(String pid);

    /**
     * Find digital object by PID with priority ordering based on version and user
     * type.
     * Priority order:
     * 1. Matching version
     * 2. Current user's objects
     * 3. PERO user's objects
     * 4. ALTO_EDITOR user's objects
     */
    @Query("""
                SELECT d
                FROM DigitalObject d
                WHERE d.pid = :pid
                    AND d.userId IN (:currentUser, :peroUser, :altoEditorUser)
                ORDER BY CASE
                    WHEN :version IS NOT NULL AND d.version = :version THEN 0
                    WHEN d.userId = :currentUser THEN 1
                    WHEN d.userId = :peroUser THEN 2
                    WHEN d.userId = :altoEditorUser THEN 3
                    ELSE 4
                END
                LIMIT 1
            """)
    Optional<DigitalObject> findByPidAndVersionAndUsersWithPriority(
            @Param("pid") String pid,
            @Param("version") Integer version,
            @Param("currentUser") Integer currentUser,
            @Param("peroUser") Integer peroUser,
            @Param("altoEditorUser") Integer altoEditorUser);

    /**
     * Find the best candidate for update based on state and user priority.
     * Priority order:
     * 1. User's own NEW or EDITED objects
     * 2. UPLOADED objects (any user)
     * 3. ALTO user's NEW objects
     * Within same priority, orders by most recent update
     */
    @Query("""
                SELECT d FROM DigitalObject d
                WHERE d.pid = :pid
                    AND (
                        (d.userId = :userId AND d.state IN (DigitalObjectState.NEW, DigitalObjectState.EDITED))
                        OR d.state = DigitalObjectState.UPLOADED
                        OR (d.userId = :altoUserId AND d.state = DigitalObjectState.NEW)
                    )
                ORDER BY
                    CASE
                        WHEN d.userId = :userId AND d.state IN (DigitalObjectState.NEW, DigitalObjectState.EDITED) THEN 0
                        WHEN d.state = DigitalObjectState.UPLOADED THEN 1
                        WHEN d.userId = :altoUserId AND d.state = DigitalObjectState.NEW THEN 2
                        ELSE 3
                    END,
                    d.date DESC
                LIMIT 1
            """)
    Optional<DigitalObject> findUpdateCandidate(
            @Param("pid") String pid,
            @Param("userId") Integer userId,
            @Param("altoUserId") Integer altoUserId);
}