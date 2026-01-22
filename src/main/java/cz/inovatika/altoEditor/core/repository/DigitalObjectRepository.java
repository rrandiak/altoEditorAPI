package cz.inovatika.altoEditor.core.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import cz.inovatika.altoEditor.core.entity.DigitalObject;

import java.util.List;
import java.util.Optional;

@Repository
public interface DigitalObjectRepository
        extends JpaRepository<DigitalObject, Integer>,
        JpaSpecificationExecutor<DigitalObject> {

    @Query("""
                SELECT d
                FROM DigitalObject d
                WHERE d.pid = :pid
                  AND d.rUserId IN (:currentUser, :peroUser, :altoUser)
                  AND (:versionXml IS NULL OR d.versionXml = :versionXml)
                ORDER BY CASE
                            WHEN d.versionXml = :versionXml THEN 0
                            WHEN d.rUserId = :currentUser THEN 1
                            WHEN d.rUserId = :peroUser THEN 2
                            WHEN d.rUserId = :altoUser THEN 3
                            ELSE 4
                         END
            """)
    List<DigitalObject> findByPidAndUsersAndVersionPriority(
            @Param("pid") String pid,
            @Param("versionXml") String versionXml,
            @Param("currentUser") Integer currentUser,
            @Param("peroUser") Integer peroUser,
            @Param("altoUser") Integer altoUser);

    List<DigitalObject> findByPid(String pid);

    List<DigitalObject> findAllByPidAndRUserId(String pid, Integer rUserId);

    Optional<DigitalObject> findByPidAndRUserId(String pid, Integer rUserId);

    Optional<DigitalObject> findByPidAndRUserIdAndVersion(String pid, Integer rUserId, String version);

    boolean existsByPid(String pid);

    @Query("""
            select d from DigitalObject d
            where d.pid = :pid
              and (
                    (d.rUserId = :userId and d.state in ('NEW', 'EDITED'))
                 or d.state = 'UPLOADED'
                 or (d.rUserId = :altoUserId and d.state = 'NEW')
              )
            order by
                case
                    when d.rUserId = :userId and d.state in ('NEW', 'EDITED') then 0
                    when d.state = 'UPLOADED' then 1
                    when d.rUserId = :altoUserId and d.state = 'NEW' then 2
                    else 3
                end,
                d.updated desc
            """)
    Optional<DigitalObject> findUpdateCandidate(String pid, Integer userId, Integer altoUserId);

    @Query("""
                select d from DigitalObject d
                where d.pid = :pid
                  and d.version = (
                      select max(d2.version)
                      from DigitalObject d2
                      where d2.pid = :pid
                  )
            """)
    List<DigitalObject> findWithMaxVersionByPid(@Param("pid") String pid);

    Optional<DigitalObject> findByPidAndInstanceId(String pid, String instanceId);
}