package cz.inovatika.altoEditor.domain.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Stack;
import java.util.UUID;
import java.util.stream.Collectors;

import org.hibernate.search.engine.search.query.SearchResult;
import org.hibernate.search.mapper.orm.Search;
import org.hibernate.search.mapper.orm.session.SearchSession;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cz.inovatika.altoEditor.domain.enums.AltoVersionState;
import cz.inovatika.altoEditor.domain.enums.Datastream;
import cz.inovatika.altoEditor.domain.model.AltoVersion;
import cz.inovatika.altoEditor.domain.model.DigitalObject;
import cz.inovatika.altoEditor.domain.repository.AltoVersionRepository;
import cz.inovatika.altoEditor.domain.repository.DigitalObjectRepository;
import cz.inovatika.altoEditor.domain.service.container.AltoVersionUploadContent;
import cz.inovatika.altoEditor.domain.service.container.AltoVersionWithContent;
import cz.inovatika.altoEditor.exception.AltoNotFoundException;
import cz.inovatika.altoEditor.exception.AltoVersionAlreadyExistsException;
import cz.inovatika.altoEditor.exception.AltoVersionNotFoundException;
import cz.inovatika.altoEditor.infrastructure.editor.AltoXmlService;
import cz.inovatika.altoEditor.infrastructure.kramerius.KrameriusService;
import cz.inovatika.altoEditor.infrastructure.kramerius.model.KrameriusObjectMetadata;
import cz.inovatika.altoEditor.infrastructure.storage.AkubraService;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AltoVersionService {

    private final AltoVersionRepository repository;

    private final DigitalObjectService digitalObjectService;

    private final DigitalObjectRepository digitalObjectRepository;

    private final UserService userService;

    private final AkubraService akubraService;

    private final KrameriusService krameriusService;

    private final AltoXmlService altoXmlService;

    private final EntityManager entityManager;

    @Transactional(readOnly = true)
    public SearchResult<AltoVersion> searchRelated(
            Long userId,
            String instance,
            String targetPid,
            String hierarchyPid,
            String title,
            LocalDateTime createdAfter,
            LocalDateTime createdBefore,
            List<AltoVersionState> states,
            int offset,
            int limit) {

        SearchSession session = Search.session(entityManager);

        String username = userService.getUserById(userId).getUsername();

        var query = session.search(AltoVersion.class)
                .where(f -> {
                    var bool = f.bool();
                    // userId or ACTIVE state
                    var userOrActive = f.bool()
                            .should(f.match().field("username").matching(username))
                            .should(f.match().field("state").matching(AltoVersionState.ACTIVE.name()));
                    bool.must(userOrActive);
                    if (instance != null) {
                        bool.must(f.match().field("instance").matching(instance));
                    }
                    if (targetPid != null) {
                        bool.must(f.match().field("pagePid").matching(targetPid));
                    }
                    if (hierarchyPid != null) {
                        bool.must(f.terms().field("ancestorPids").matchingAny(hierarchyPid));
                    }
                    if (title != null) {
                        // Match title in either pageTitle or ancestorTitles
                        var titleOr = f.bool()
                                .should(f.wildcard().field("pageTitle").matching("*" + title + "*"))
                                .should(f.wildcard().field("ancestorTitles").matching("*" + title + "*"));
                        bool.must(titleOr);
                    }
                    if (createdAfter != null) {
                        bool.must(f.range().field("createdAt").atLeast(createdAfter));
                    }
                    if (createdBefore != null) {
                        bool.must(f.range().field("createdAt").atMost(createdBefore));
                    }
                    if (states != null && !states.isEmpty()) {
                        bool.must(f.terms().field("state").matchingAny(states.stream().map(Enum::name).toList()));
                    }
                    return bool;
                });

        return query.fetch(limit, offset);
    }

    @Transactional(readOnly = true)
    public SearchResult<AltoVersion> search(
            List<Long> users,
            String instance,
            String targetPid,
            String hierarchyPid,
            String title,
            LocalDateTime createdAfter,
            LocalDateTime createdBefore,
            List<AltoVersionState> states,
            int offset,
            int limit) {

        SearchSession session = Search.session(entityManager);

        List<String> usernames = users != null ? users.stream()
                .map(id -> userService.getUserById(id).getUsername())
                .collect(Collectors.toList()) : null;

        var query = session.search(AltoVersion.class)
                .where(f -> {
                    var bool = f.bool();
                    if (users != null && !users.isEmpty()) {
                        bool.must(f.terms().field("username").matchingAny(usernames));
                    }
                    if (instance != null) {
                        bool.must(f.match().field("instance").matching(instance));
                    }
                    if (targetPid != null) {
                        bool.must(f.match().field("pagePid").matching(targetPid));
                    }
                    if (hierarchyPid != null) {
                        bool.must(f.terms().field("ancestorPids").matchingAny(hierarchyPid));
                    }
                    if (title != null) {
                        // Match title in either pageTitle or ancestorTitles
                        var titleOr = f.bool()
                                .should(f.wildcard().field("pageTitle").matching("*" + title + "*"))
                                .should(f.wildcard().field("ancestorTitles").matching("*" + title + "*"));
                        bool.must(titleOr);
                    }
                    if (createdAfter != null) {
                        bool.must(f.range().field("createdAt").atLeast(createdAfter));
                    }
                    if (createdBefore != null) {
                        bool.must(f.range().field("createdAt").atMost(createdBefore));
                    }
                    if (states != null && !states.isEmpty()) {
                        bool.must(f.terms().field("state").matchingAny(states.stream().map(Enum::name).toList()));
                    }
                    return bool;
                });

        return query.fetch(limit, offset);
    }

    private UUID parseUuid(String pid) {
        if (!pid.startsWith("uuid:")) {
            throw new IllegalArgumentException("PID must start with 'uuid:'");
        }

        return UUID.fromString(pid.substring(5));
    }

    /**
     * The ALTO content is retrieved in the following order:
     * 1. The version owned by the current user.
     * 2. The version currently in 'ACTIVE' state.
     */
    public Optional<AltoVersion> findRelated(String pid, Long userId) {
        return repository.findRelated(this.parseUuid(pid), userId).stream().findFirst();
    }

    /**
     * The ALTO content is retrieved in the following order:
     * 1. The version owned by the current user.
     * 2. The version currently in 'ACTIVE' state.
     */
    public AltoVersionWithContent findRelatedAlto(String pid, Long userId) {
        Optional<AltoVersion> digitalObject = this.findRelated(pid, userId);

        if (digitalObject.isPresent()) {
            AltoVersion obj = digitalObject.get();

            byte[] content = akubraService.retrieveDsBinaryContent(
                    obj.getDigitalObject().getPid(),
                    Datastream.ALTO,
                    obj.getVersion());

            return new AltoVersionWithContent(obj, content);
        }

        return null;
    }

    public AltoVersionWithContent fetchNewAlto(String pid, String instance, Long userId, String token) {
        if (repository.existsByDigitalObjectUuid(this.parseUuid(pid))) {
            throw new AltoVersionAlreadyExistsException("Digital object with PID already exists: " + pid);
        }

        byte[] foxml = krameriusService.getFoxmlBytes(pid, instance, token);
        byte[] alto = akubraService.getLatestDsVersionBinaryContent(foxml, Datastream.ALTO);

        if (alto == null) {
            throw new AltoNotFoundException("ALTO datastream not found in FOXML for PID: " + pid);
        }

        Stack<KrameriusObjectMetadata> metadataStack = new Stack<>();

        KrameriusObjectMetadata metadata = krameriusService.getObjectMetadata(pid, instance, token);
        while (metadata != null) {
            metadataStack.push(metadata);
            metadata = krameriusService.getObjectMetadata(metadata.getParentPid(), instance, token);
        }

        DigitalObject targetObj = null;
        for (KrameriusObjectMetadata meta : metadataStack) {
            if (digitalObjectRepository.existsById(this.parseUuid(meta.getPid()))) {
                targetObj = digitalObjectRepository.findById(this.parseUuid(meta.getPid())).get();
                continue;
            }
            targetObj = digitalObjectService.createFromKrameriusMetadata(meta, targetObj);
        }

        akubraService.saveAltoContent(
                pid,
                0,
                alto);

        AltoVersion obj = repository.save(
                AltoVersion.builder().user(userService.getUserById(userId))
                        .digitalObject(targetObj).instance(instance)
                        .version(0).build());

        byte[] content = akubraService.retrieveDsBinaryContent(
                obj.getDigitalObject().getPid(),
                Datastream.ALTO,
                obj.getVersion());

        return new AltoVersionWithContent(obj, content);
    }

    public AltoVersionWithContent getAltoVersion(String pid, Integer version) {
        Optional<AltoVersion> digitalObject = repository
                .findByDigitalObjectUuidAndVersion(this.parseUuid(pid), version)
                .stream()
                .findFirst();

        if (digitalObject.isEmpty()) {
            throw new AltoVersionNotFoundException(
                    "ALTO version not found for PID: " + pid + " and version: " + version);
        }

        AltoVersion obj = digitalObject.get();

        byte[] content = akubraService.retrieveDsBinaryContent(
                obj.getDigitalObject().getPid(),
                Datastream.ALTO,
                obj.getVersion());

        return new AltoVersionWithContent(obj, content);
    }

    public AltoVersionWithContent getActiveAlto(String pid) {
        Optional<AltoVersion> digitalObject = repository
                .findActive(this.parseUuid(pid))
                .stream()
                .findFirst();

        if (digitalObject.isEmpty()) {
            throw new AltoVersionNotFoundException("Original ALTO not found for PID: " + pid);
        }

        AltoVersion obj = digitalObject.get();

        byte[] content = akubraService.retrieveDsBinaryContent(
                obj.getDigitalObject().getPid(),
                Datastream.ALTO,
                obj.getVersion());

        return new AltoVersionWithContent(obj, content);
    }

    private AltoVersionWithContent createNewAltoVersion(String pid, Long userId, String altoContent) {
        Optional<AltoVersion> objOpt = repository.findFirstByDigitalObjectUuidOrderByVersionDesc(this.parseUuid(pid))
                .stream()
                .findFirst();

        if (objOpt.isEmpty()) {
            throw new AltoVersionNotFoundException("Digital object not found for PID: " + pid);
        }

        AltoVersion obj = objOpt.get();
        int newVersion = obj.getVersion() + 1;

        akubraService.saveAltoContent(
                pid,
                newVersion,
                altoContent.getBytes());

        AltoVersion newAltoVersion = repository.save(AltoVersion.builder()
                .user(obj.getUser())
                .instance(obj.getInstance())
                .digitalObject(obj.getDigitalObject())
                .version(newVersion)
                .state(AltoVersionState.PENDING)
                .build());

        return new AltoVersionWithContent(newAltoVersion, altoContent.getBytes());
    }

    private AltoVersionWithContent updateAltoVersion(String pid, Long userId, String altoContent) {
        Optional<AltoVersion> objOpt = repository.findRelated(this.parseUuid(pid), userId);

        if (objOpt.isEmpty()) {
            throw new AltoNotFoundException("No ALTO version available for update for PID: " + pid);
        }

        akubraService.saveAltoContent(
                pid,
                objOpt.get().getVersion(),
                altoContent.getBytes());

        AltoVersion updatedAltoVersion = objOpt.get();
        updatedAltoVersion.setState(AltoVersionState.PENDING);
        repository.save(updatedAltoVersion);

        return new AltoVersionWithContent(updatedAltoVersion, altoContent.getBytes());
    }

    public AltoVersionWithContent updateOrCreateAlto(String pid, Long userId, String altoContent) {
        Optional<AltoVersion> objOpt = repository.findByDigitalObjectUuidAndUserId(this.parseUuid(pid), userId);

        if (objOpt.isEmpty()) {
            return createNewAltoVersion(pid, userId, altoContent);
        }

        if (objOpt.get().getState() == AltoVersionState.PENDING) {
            return updateAltoVersion(pid, userId, altoContent);
        }

        return createNewAltoVersion(pid, userId, altoContent);
    }

    public String getOcr(Integer objectId) {
        Optional<AltoVersion> objOpt = repository.findById(objectId);

        if (objOpt.isEmpty()) {
            throw new AltoVersionNotFoundException("Digital object not found for ID: " + objectId);
        }

        AltoVersion obj = objOpt.get();

        byte[] content = akubraService.retrieveDsBinaryContent(
                obj.getDigitalObject().getPid(),
                Datastream.ALTO,
                obj.getVersion());

        return altoXmlService.convertAltoToOcr(content);
    }

    public byte[] getKrameriusObjectImage(String pid, String instanceId, String token) {
        return krameriusService.getImageBytes(pid, instanceId, token);
    }

    public void setObjectActive(int objectId) {
        AltoVersion digitalObject = repository.findById(objectId)
                .orElseThrow(() -> new RuntimeException("Digital object not found with ID: " + objectId));

        digitalObject.setState(AltoVersionState.ACTIVE);

        List<AltoVersion> updated = repository.findAllByDigitalObjectUuid(digitalObject.getDigitalObject().getUuid())
                .stream()
                .filter(obj -> obj.getId() != digitalObject.getId() && obj.getState() == AltoVersionState.ACTIVE)
                .map(obj -> {
                    obj.setState(AltoVersionState.ARCHIVED);
                    return obj;
                })
                .collect(Collectors.toList());

        updated.add(digitalObject);

        repository.saveAll(updated);
    }

    public void setStateForObject(int objectId, AltoVersionState state) {
        AltoVersion digitalObject = repository.findById(objectId)
                .orElseThrow(() -> new RuntimeException("Digital object not found with ID: " + objectId));

        digitalObject.setState(state);

        repository.save(digitalObject);
    }

    public AltoVersionUploadContent getAltoVersionUploadContent(int objectId) {
        AltoVersion obj = repository.findById(objectId)
                .orElseThrow(() -> new RuntimeException("Digital object not found with ID: " + objectId));

        return new AltoVersionUploadContent(obj.getDigitalObject().getPid(),
                obj.getInstance(),
                akubraService.retrieveDsBinaryContent(
                        obj.getDigitalObject().getPid(),
                        Datastream.ALTO,
                        obj.getVersion()),
                akubraService.retrieveDsBinaryContent(
                        obj.getDigitalObject().getPid(),
                        Datastream.TEXT_OCR,
                        obj.getVersion()));
    }
}