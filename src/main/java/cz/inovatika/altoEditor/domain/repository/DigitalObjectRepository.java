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
    List<DigitalObject> findAllByPid(String pid);

    /**
     * Check if any digital object exists with the given PID
     */
    boolean existsByPid(String pid);

    /**
     * Find active digital objects by PID
     */
    @Query("""
                SELECT d
                FROM DigitalObject d
                WHERE d.pid = :pid
                AND d.state = DigitalObjectState.ACTIVE
            """)
    Optional<DigitalObject> findActive(@Param("pid") String pid);

    /**
     * Find digital object by PID and user ID
     */
    Optional<DigitalObject> findByPidAndUserId(String pid, Integer userId);

    /**
     * Find digital object by PID and version
     */
    Optional<DigitalObject> findByPidAndVersion(String pid, Integer version);

    /**
     * Find digital object by PID and instance ID
     */
    Optional<DigitalObject> findByPidAndInstanceId(String pid, String instanceId);

    /**
     * Find the digital object with the highest version for the given PID
     */
    Optional<DigitalObject> findFirstByPidOrderByVersionDesc(String pid);

    /**
     * Find digital object by PID with priority ordering based on version and user
     * type.
     * The digital object is retrieved in the following order:
     * 1. The version owned by the current user.
     * 2. The version currently in 'ACTIVE' state.
     */
    @Query("""
                SELECT d
                FROM DigitalObject d
                WHERE d.pid = :pid
                    AND (d.user.id = :userId
                        OR d.state = DigitalObjectState.ACTIVE)
                ORDER BY CASE
                    WHEN d.user.id = :userId THEN 0
                    ELSE 1
                END
                LIMIT 1
            """)
    Optional<DigitalObject> findRelated(
            @Param("pid") String pid,
            @Param("userId") Integer userId);
}