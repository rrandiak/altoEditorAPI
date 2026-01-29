package cz.inovatika.altoEditor.domain.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cz.inovatika.altoEditor.domain.enums.Datastream;
import cz.inovatika.altoEditor.domain.enums.DigitalObjectState;
import cz.inovatika.altoEditor.domain.model.DigitalObject;
import cz.inovatika.altoEditor.domain.repository.DigitalObjectRepository;
import cz.inovatika.altoEditor.domain.repository.UserRepository;
import cz.inovatika.altoEditor.domain.repository.spec.DigitalObjectSpecification;
import cz.inovatika.altoEditor.domain.service.container.DigitalObjectUploadContent;
import cz.inovatika.altoEditor.domain.service.container.DigitalObjectWithContent;
import cz.inovatika.altoEditor.infrastructure.editor.AltoXmlService;
import cz.inovatika.altoEditor.infrastructure.kramerius.KrameriusService;
import cz.inovatika.altoEditor.infrastructure.storage.AkubraService;

import cz.inovatika.altoEditor.exception.DigitalObjectAlreadyExistsException;
import cz.inovatika.altoEditor.exception.AltoNotFoundException;
import cz.inovatika.altoEditor.exception.AltoVersionNotFoundException;
import cz.inovatika.altoEditor.exception.DigitalObjectNotFoundException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DigitalObjectService {

    private final DigitalObjectRepository repository;

    private final UserRepository userRepository;

    private final AkubraService akubraService;

    private final KrameriusService krameriusService;

    private final AltoXmlService altoXmlService;

    @Transactional(readOnly = true)
    public Page<DigitalObject> search(
            List<Integer> users,
            String instanceId,
            String targetPid,
            String hierarchyPid,
            String label,
            String title,
            LocalDateTime createdAfter,
            LocalDateTime createdBefore,
            List<DigitalObjectState> states,
            Pageable pageable) {

        List<Specification<DigitalObject>> userSpecs = users != null ? users.stream()
                .map(DigitalObjectSpecification::hasUser)
                .collect(Collectors.toList())
                : List.of();

        Specification<DigitalObject> spec = Specification.allOf(
                Specification.anyOf(userSpecs),
                DigitalObjectSpecification.hasInstance(instanceId),
                DigitalObjectSpecification.hasPid(targetPid),
                DigitalObjectSpecification.hasLabelLike(label),
                DigitalObjectSpecification.hasTitleLike(title),
                DigitalObjectSpecification.createdAfter(createdAfter),
                DigitalObjectSpecification.createdBefore(createdBefore),
                DigitalObjectSpecification.hasStateIn(states));

        return repository.findAll(spec, pageable);
    }

    /**
     * The ALTO content is retrieved in the following order:
     * 1. The version owned by the current user.
     * 2. The version currently in 'ACTIVE' state.
     * */
    public Optional<DigitalObject> findRelated(String pid, Integer userId) {
        return repository.findRelated(pid, userId).stream().findFirst();
    }

    /**
     * The ALTO content is retrieved in the following order:
     * 1. The version owned by the current user.
     * 2. The version currently in 'ACTIVE' state.
     * */
    public DigitalObjectWithContent findRelatedAlto(String pid, Integer userId) {
        Optional<DigitalObject> digitalObject = this.findRelated(pid, userId);

        if (digitalObject.isPresent()) {
            DigitalObject obj = digitalObject.get();

            byte[] content = akubraService.retrieveDsBinaryContent(
                    obj.getPid(),
                    Datastream.ALTO,
                    obj.getVersion());

            return new DigitalObjectWithContent(obj, content);
        }

        return null;
    }

    public DigitalObjectWithContent fetchNewAlto(String pid, String instanceId, Integer userId, String token) {
        if (repository.existsByPid(pid)) {
            throw new DigitalObjectAlreadyExistsException("Digital object with PID already exists: " + pid);
        }

        byte[] foxml = krameriusService.getFoxmlBytes(pid, instanceId, token);

        byte[] alto = akubraService.getLatestDsVersionBinaryContent(foxml, Datastream.ALTO);

        if (alto == null) {
            throw new AltoNotFoundException("ALTO datastream not found in FOXML for PID: " + pid);
        }

        Optional<DigitalObject> maxVersionObj = repository.findFirstByPidOrderByVersionDesc(pid);

        DigitalObject obj = repository.save(
                DigitalObject.builder().user(userRepository.findById(userId).orElse(null))
                        .instanceId(instanceId).pid(pid)
                        .version(maxVersionObj.map(o -> o.getVersion() + 1).orElse(0)).build());

        byte[] content = akubraService.retrieveDsBinaryContent(
                obj.getPid(),
                Datastream.ALTO,
                obj.getVersion());

        return new DigitalObjectWithContent(obj, content);
    }

    public DigitalObjectWithContent getAltoVersion(String pid, Integer version) {
        Optional<DigitalObject> digitalObject = repository
                .findByPidAndVersion(pid, version)
                .stream()
                .findFirst();

        if (digitalObject.isEmpty()) {
            throw new AltoVersionNotFoundException("ALTO version not found for PID: " + pid + " and version: " + version);
        }

        DigitalObject obj = digitalObject.get();

        byte[] content = akubraService.retrieveDsBinaryContent(
                obj.getPid(),
                Datastream.ALTO,
                obj.getVersion());

        return new DigitalObjectWithContent(obj, content);
    }

    public DigitalObjectWithContent getActiveAlto(String pid) {
        Optional<DigitalObject> digitalObject = repository
                .findActive(pid)
                .stream()
                .findFirst();

        if (digitalObject.isEmpty()) {
            throw new AltoVersionNotFoundException("Original ALTO not found for PID: " + pid);
        }

        DigitalObject obj = digitalObject.get();

        byte[] content = akubraService.retrieveDsBinaryContent(
                obj.getPid(),
                Datastream.ALTO,
                obj.getVersion());

        return new DigitalObjectWithContent(obj, content);
    }

    private DigitalObjectWithContent createNewAltoVersion(String pid, Integer userId, String altoContent) {
        Optional<DigitalObject> objOpt = repository.findFirstByPidOrderByVersionDesc(pid)
                .stream()
                .findFirst();

        if (objOpt.isEmpty()) {
            throw new DigitalObjectNotFoundException("Digital object not found for PID: " + pid);
        }

        DigitalObject obj = objOpt.get();
        int newVersion = obj.getVersion() + 1;

        akubraService.saveAltoContent(
                pid,
                newVersion,
                altoContent.getBytes());

        DigitalObject newDigitalObject = repository.save(DigitalObject.builder()
                .user(obj.getUser())
                .instanceId(obj.getInstanceId())
                .pid(pid)
                .version(newVersion)
                .state(DigitalObjectState.PENDING)
                .build());

        return new DigitalObjectWithContent(newDigitalObject, altoContent.getBytes());
    }

    private DigitalObjectWithContent updateAltoVersion(String pid, Integer userId, String altoContent) {
        Optional<DigitalObject> objOpt = repository.findRelated(pid, userId);

        if (objOpt.isEmpty()) {
            throw new AltoNotFoundException("No ALTO version available for update for PID: " + pid);
        }

        akubraService.saveAltoContent(
                pid,
                objOpt.get().getVersion(),
                altoContent.getBytes());

        DigitalObject updatedDigitalObject = objOpt.get();
        updatedDigitalObject.setState(DigitalObjectState.PENDING);
        repository.save(updatedDigitalObject);

        return new DigitalObjectWithContent(updatedDigitalObject, altoContent.getBytes());
    }

    public DigitalObjectWithContent updateOrCreateAlto(String pid, Integer userId, String altoContent) {
        Optional<DigitalObject> objOpt = repository.findByPidAndUserId(pid, userId);

        if (objOpt.isEmpty()) {
            return createNewAltoVersion(pid, userId, altoContent);
        }

        if (objOpt.get().getState() == DigitalObjectState.PENDING) {
            return updateAltoVersion(pid, userId, altoContent);
        }

        return createNewAltoVersion(pid, userId, altoContent);
    }

    public String getOcr(Integer objectId) {
        Optional<DigitalObject> objOpt = repository.findById(objectId);

        if (objOpt.isEmpty()) {
            throw new DigitalObjectNotFoundException("Digital object not found for ID: " + objectId);
        }

        DigitalObject obj = objOpt.get();

        byte[] content = akubraService.retrieveDsBinaryContent(
                obj.getPid(),
                Datastream.ALTO,
                obj.getVersion());

        return altoXmlService.convertAltoToOcr(content);
    }

    public byte[] getKrameriusObjectImage(String pid, String instanceId, String token) {
        return krameriusService.getImageBytes(pid, instanceId, token);
    }

    public void setObjectActive(int objectId) {
        DigitalObject digitalObject = repository.findById(objectId)
                .orElseThrow(() -> new RuntimeException("Digital object not found with ID: " + objectId));

        digitalObject.setState(DigitalObjectState.ACTIVE);

        List<DigitalObject> updated = repository.findAllByPid(digitalObject.getPid()).stream()
                .filter(obj -> obj.getId() != digitalObject.getId() && obj.getState() == DigitalObjectState.ACTIVE)
                .map(obj -> {
                    obj.setState(DigitalObjectState.ARCHIVED);
                    return obj;
                })
                .collect(Collectors.toList());

        updated.add(digitalObject);

        repository.saveAll(updated);
    }

    public void setStateForObject(int objectId, DigitalObjectState state) {
        DigitalObject digitalObject = repository.findById(objectId)
                .orElseThrow(() -> new RuntimeException("Digital object not found with ID: " + objectId));

        digitalObject.setState(state);

        repository.save(digitalObject);
    }

    public DigitalObjectUploadContent getDigitalObjectUploadContent(int objectId) {
        DigitalObject obj = repository.findById(objectId)
                .orElseThrow(() -> new RuntimeException("Digital object not found with ID: " + objectId));

        return new DigitalObjectUploadContent(obj.getPid(),
                obj.getInstanceId(),
                akubraService.retrieveDsBinaryContent(
                        obj.getPid(),
                        Datastream.ALTO,
                        obj.getVersion()),
                akubraService.retrieveDsBinaryContent(
                        obj.getPid(),
                        Datastream.TEXT_OCR,
                        obj.getVersion()));
    }
}