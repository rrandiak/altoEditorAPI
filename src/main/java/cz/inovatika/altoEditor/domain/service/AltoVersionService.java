package cz.inovatika.altoEditor.domain.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.hibernate.search.engine.search.query.SearchResult;
import org.hibernate.search.mapper.orm.Search;
import org.hibernate.search.mapper.orm.session.SearchSession;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cz.inovatika.altoEditor.domain.adapter.PidAdapter;
import cz.inovatika.altoEditor.domain.enums.AltoVersionState;
import cz.inovatika.altoEditor.domain.enums.BatchPriority;
import cz.inovatika.altoEditor.domain.enums.BatchType;
import cz.inovatika.altoEditor.domain.enums.Datastream;
import cz.inovatika.altoEditor.domain.model.AltoVersion;
import cz.inovatika.altoEditor.domain.model.Batch;
import cz.inovatika.altoEditor.domain.model.DigitalObject;
import cz.inovatika.altoEditor.domain.repository.AltoVersionRepository;
import cz.inovatika.altoEditor.domain.repository.BatchRepository;
import cz.inovatika.altoEditor.domain.repository.UserRepository;
import cz.inovatika.altoEditor.domain.service.container.AltoVersionUploadContent;
import cz.inovatika.altoEditor.domain.service.container.AltoVersionWithContent;
import cz.inovatika.altoEditor.exception.AltoNotFoundException;
import cz.inovatika.altoEditor.exception.AltoVersionAlreadyExistsException;
import cz.inovatika.altoEditor.exception.AltoVersionNotFoundException;
import cz.inovatika.altoEditor.infrastructure.editor.AltoXmlService;
import cz.inovatika.altoEditor.infrastructure.kramerius.KrameriusService;
import cz.inovatika.altoEditor.infrastructure.process.ProcessDispatcher;
import cz.inovatika.altoEditor.infrastructure.process.altoocr.AltoOcrGeneratorProcessFactory;
import cz.inovatika.altoEditor.infrastructure.storage.AkubraService;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AltoVersionService {

    private final AltoVersionRepository repository;

    private final ObjectHierarchyService objectHierarchyService;

    private final UserService userService;

    private final AkubraService akubraService;

    private final KrameriusService krameriusService;

    private final AltoXmlService altoXmlService;

    private final EntityManager entityManager;

    private final UserRepository userRepository;

    private final BatchRepository batchRepository;

    private final ProcessDispatcher processDispatcher;

    private final AltoOcrGeneratorProcessFactory processFactory;

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
            String ancestorPid,
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
                    if (ancestorPid != null) {
                        bool.must(f.terms().field("ancestorPids").matchingAny(ancestorPid));
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
    public List<String> distinctPidsByAncestorPid(String ancestorPid) {
        return Search.session(entityManager)
                .search(AltoVersion.class)
                .select(f -> f.field("pid", String.class))
                .where(f -> f.terms().field("ancestorPids").matchingAny(ancestorPid))
                .fetchAllHits().stream()
                .distinct()
                .toList();
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

    /**
     * Fetch new ALTO from Kramerius and create the initial ALTO version.
     * In process, fetches and creates the necessary DigitalObject hierarchy
     * entries.
     * 
     * Preconditions:
     * - no ALTO version exists for the given PID
     * - user permission was checked before calling this method
     */
    public AltoVersionWithContent createInitialVersion(String pid, String instance) {
        if (repository.existsByDigitalObjectUuid(this.parseUuid(pid))) {
            throw new AltoVersionAlreadyExistsException("Digital object with PID already exists: " + pid);
        }

        byte[] foxml = krameriusService.getFoxmlBytes(pid, instance);
        byte[] content = akubraService.getLatestDsVersionBinaryContent(foxml, Datastream.ALTO);

        if (content == null) {
            throw new AltoNotFoundException("ALTO datastream not found in FOXML for PID: " + pid);
        }

        DigitalObject targetObj = objectHierarchyService.fetchAndStore(pid, instance);

        akubraService.saveAltoContent(pid, 0, content);

        // Save under user of used Kramerius instance
        AltoVersion obj = repository.save(
                AltoVersion.builder().user(userService.getUserByUsername(instance))
                        .digitalObject(targetObj)
                        .version(0).build());

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
        Optional<AltoVersion> digitalObject = repository.findActive(this.parseUuid(pid));

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

    private AltoVersion createNewAltoVersion(
            String pid, Long userId, byte[] altoContent, AltoVersionState state) {
        Optional<AltoVersion> objOpt = repository
                .findFirstByDigitalObjectUuidOrderByVersionDesc(PidAdapter.toUuid(pid));

        if (objOpt.isEmpty()) {
            throw new AltoVersionNotFoundException("Digital object not found for PID: " + pid);
        }

        AltoVersion obj = objOpt.get();
        int newVersion = obj.getVersion() + 1;

        akubraService.saveAltoContent(pid, newVersion, altoContent);

        return repository.save(AltoVersion.builder()
                .user(obj.getUser())
                .digitalObject(obj.getDigitalObject())
                .version(newVersion)
                .state(state)
                .build());
    }

    @Transactional
    private void replaceInstanceAssociation(AltoVersion altoVersion, String instance) {
        repository.removeInstanceAssociation(PidAdapter.toUuid(altoVersion.getPid()), instance);
        altoVersion.getPresentInInstances().add(instance);
        repository.save(altoVersion);
    }

    /**
     * 1. If a PENDING version for the user exists, update its content.
     * 2. If a PENDING version does not exist, create a new ALTO version.
     * 
     * Preconditions:
     * - DigitalObject for the given PID exists
     */
    public AltoVersionWithContent updateOrCreateVersion(String pid, Long userId, byte[] altoContent) {
        Optional<AltoVersion> altoVersionOpt = repository.findPendingForUser(PidAdapter.toUuid(pid), userId);

        if (altoVersionOpt.isPresent()) {
            AltoVersion altoVersion = altoVersionOpt.get();

            akubraService.saveAltoContent(altoVersion.getPid(), altoVersion.getVersion(), altoContent);

            altoVersion.setState(AltoVersionState.PENDING);
            repository.save(altoVersion);

            return new AltoVersionWithContent(altoVersion, altoContent);
        }

        return new AltoVersionWithContent(
                createNewAltoVersion(pid, userId, altoContent, AltoVersionState.PENDING),
                altoContent);
    }

    /**
     * 1. If no version exists, create new version and set it as ACTIVE.
     * Associate instance given by username with this version.
     * 2. If ACTIVE version exists, this instance is associated with it and it has
     * same content, then do nothing and return this version.
     * 3. If STALE version of this instance exists, this instance is associated with
     * it and has same content, then do nothing and return this version.
     * 4. Otherwise, create new STALE version, associate this instance with this new
     * version and change old STALE version to ARCHIVED.
     * 
     * Preconditions:
     * - DigitalObject for the given PID exists
     */
    public AltoVersion updateOrCreateKrameriusVersion(String pid, Long userId, byte[] content) {
        Optional<AltoVersion> altoVersionOpt = repository.findActive(PidAdapter.toUuid(pid));

        if (altoVersionOpt.isEmpty()) {
            AltoVersion altoVersion = createNewAltoVersion(pid, userId, content, AltoVersionState.ACTIVE);
            altoVersion.getPresentInInstances().add(userService.getUserById(userId).getUsername());
            repository.save(altoVersion);
            return altoVersion;
        }

        AltoVersion altoVersion = altoVersionOpt.get();
        String contentHash = altoXmlService.computeHash(content);
        String instance = userService.getUserById(userId).getUsername();

        if (altoVersion.getPresentInInstances().contains(instance)
                && altoVersion.getContentHash() == contentHash) {
            return altoVersion;
        }

        Optional<AltoVersion> staleVersionOpt = repository.findStaleForUser(PidAdapter.toUuid(pid), userId);

        if (staleVersionOpt.isPresent() &&
                staleVersionOpt.get().getPresentInInstances().contains(instance)
                && staleVersionOpt.get().getContentHash() == contentHash) {
            return staleVersionOpt.get();
        }

        altoVersion = createNewAltoVersion(pid, userId, content, AltoVersionState.STALE);

        altoVersion.getPresentInInstances().add(instance);
        repository.save(altoVersion);

        return altoVersion;
    }

    /**
     * 1. If version for specified UUID and user with same content exists and is in
     * ACTIVE, PENDING or ARCHIVED state, return it.
     * Futhermore, if the version is in ARCHIVED state, change its state to PENDING.
     * 2. Otherwise, create new PENDING version.
     */
    public AltoVersion updateOrCreateEngineVersion(String pid, Long userId, byte[] content) {
        Optional<AltoVersion> altoVersionOpt = repository.findEngineUpdateCandidate(
                PidAdapter.toUuid(pid), userId, altoXmlService.computeHash(content));

        if (altoVersionOpt.isPresent()) {
            AltoVersion altoVersion = altoVersionOpt.get();

            if (altoVersion.getState() == AltoVersionState.ARCHIVED) {
                altoVersion.setState(AltoVersionState.PENDING);
                repository.save(altoVersion);
            }

            return altoVersion;
        }

        return createNewAltoVersion(pid, userId, content, AltoVersionState.PENDING);
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

    public byte[] getKrameriusObjectImage(String pid, String instance) {
        return krameriusService.getImageBytes(pid, instance);
    }

    public Batch generateAlto(String pid, String engine, BatchPriority priority, Long userId) {
        if (repository.existsByDigitalObjectUuid(PidAdapter.toUuid(pid))) {
            throw new AltoVersionNotFoundException(
                    "No digital object found for PID: " + pid + " and current user");
        }
        if (!userRepository.existsEngineByUsername(engine)) {
            throw new IllegalArgumentException("Engine '" + engine + "' is not enabled");
        }

        Batch batch = batchRepository.save(Batch.builder()
                .type(BatchType.GENERATE_SINGLE)
                .pid(pid)
                .priority(priority)
                .engine(engine)
                .createdBy(userService.getUserById(userId))
                .build());

        processDispatcher.submit(processFactory.create(batch));

        return batch;
    }

    /**
     * Set the specified ALTO version as the ACTIVE version for its PID and
     * instance.
     * 
     * This operation expects that the related ALTO and OCR content has already been
     * uploaded to all Kramerius instances.
     * And its then does the following:
     * - Changes the object's state to 'ACTIVE' (making it the default for editing
     * and viewing).
     * - Archives previous ACTIVE version for the same PID, only if the target ALTO
     * version is not already ACTIVE.
     * - Archives all STALE versions of the same PID.
     */
    public void accept(int versionId) {
        AltoVersion digitalObject = repository.findById(versionId)
                .orElseThrow(() -> new RuntimeException("ALTO version not found with ID: " + versionId));

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

    /**
     * Reject an ALTO version by its ID.
     * Only permitted state transition is PENDING -> REJECTED
     */
    public void reject(int versionId) {
        AltoVersion digitalObject = repository.findById(versionId)
                .orElseThrow(() -> new RuntimeException("ALTO version not found with ID: " + versionId));

        if (digitalObject.getState() != AltoVersionState.PENDING) {
            throw new IllegalStateException(
                    "Only PENDING ALTO versions can be rejected. Current state: " + digitalObject.getState());
        }

        digitalObject.setState(AltoVersionState.REJECTED);

        repository.save(digitalObject);
    }

    /**
     * Archive an ALTO version by its ID.
     * Only permitted state transition is PENDING -> ARCHIVED
     */
    public void archive(int versionId) {
        AltoVersion digitalObject = repository.findById(versionId)
                .orElseThrow(() -> new RuntimeException("ALTO version not found with ID: " + versionId));

        if (digitalObject.getState() != AltoVersionState.PENDING) {
            throw new IllegalStateException(
                    "Only PENDING ALTO versions can be archived. Current state: " + digitalObject.getState());
        }

        digitalObject.setState(AltoVersionState.ARCHIVED);

        repository.save(digitalObject);
    }

    public AltoVersionUploadContent getAltoVersionUploadContent(int objectId) {
        AltoVersion obj = repository.findById(objectId)
                .orElseThrow(() -> new RuntimeException("Digital object not found with ID: " + objectId));

        return new AltoVersionUploadContent(obj.getDigitalObject().getPid(),
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