package cz.inovatika.altoEditor.core.service;

import cz.inovatika.altoEditor.core.entity.DigitalObject;
import cz.inovatika.altoEditor.core.enums.Datastream;
import cz.inovatika.altoEditor.core.enums.DigitalObjectState;
import cz.inovatika.altoEditor.core.repository.DigitalObjectRepository;
import cz.inovatika.altoEditor.core.repository.UserRepository;
import cz.inovatika.altoEditor.core.repository.spec.DigitalObjectSpecifications;
import cz.inovatika.altoEditor.core.service.container.DigitalObjectUploadContent;
import cz.inovatika.altoEditor.core.service.container.DigitalObjectWithContent;
import cz.inovatika.altoEditor.editor.AltoOcrService;
import cz.inovatika.altoEditor.kramerius.KrameriusService;
import cz.inovatika.altoEditor.storage.AkubraService;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class DigitalObjectService {

    private static final String USER_ALTOEDITOR = "altoeditor";
    private static final String USER_PERO = "pero";

    private final DigitalObjectRepository repository;

    private final UserRepository userRepository;

    private final AkubraService akubraService;

    private final KrameriusService krameriusService;

    private final AltoOcrService altoOcrService;

    private Integer getUserIdByUsername(String username) {
        // TODO: throw specific exception
        return username == null ? null
                : userRepository.findByLogin(username)
                        .orElseThrow(() -> new RuntimeException("User not found: " + username)).getId();
    }

    @Transactional(readOnly = true)
    public Page<DigitalObject> search(
            String pid,
            String username,
            Pageable pageable) {
        Specification<DigitalObject> spec = Specification.allOf(
                DigitalObjectSpecifications.hasPid(pid),
                DigitalObjectSpecifications.hasUser(getUserIdByUsername(username)));

        return repository.findAll(spec, pageable);
    }

    public DigitalObjectWithContent findAlto(String pid, String version, Integer userId) {
        // Search sequence for a given PID
        // 1. By specific version
        // 2. By user
        // 3. Default version from PERO
        // 4. Default version from Kramerius
        Integer altoEditorUserId = getUserIdByUsername(USER_ALTOEDITOR);
        Integer peroUserId = getUserIdByUsername(USER_PERO);

        Optional<DigitalObject> digitalObject = repository
                .findByPidAndUsersAndVersionPriority(pid, version, userId, peroUserId, altoEditorUserId)
                .stream()
                .findFirst();

        if (digitalObject.isPresent()) {
            DigitalObject obj = digitalObject.get();

            String content = akubraService.getDatastreamContent(
                    obj.getPid(),
                    Datastream.ALTO,
                    obj.getVersion());

            return new DigitalObjectWithContent(obj, content);
        }

        return null;
    }

    public DigitalObjectWithContent fetchNewAlto(String pid, String instanceId, Integer userId, String token) {
        if (repository.existsByPid(pid)) {
            throw new RuntimeException("Digital object with PID already exists: " + pid);
        }

        byte[] foxml = krameriusService.getFoxmlBytes(pid, instanceId, token);

        akubraService.saveFoxml(pid, foxml);

        String actualVersion = akubraService.getDsVersion(pid, Datastream.ALTO);

        DigitalObject obj = repository.save(
                DigitalObject.builder().rUserId(userId).instance(instanceId).pid(pid).version(actualVersion).build());

        String content = akubraService.getDatastreamContent(
                obj.getPid(),
                Datastream.ALTO,
                obj.getVersion());

        return new DigitalObjectWithContent(obj, content);
    }

    public DigitalObjectWithContent getOriginalAlto(String pid) {
        Integer altoEditorUserId = getUserIdByUsername(USER_ALTOEDITOR);

        Optional<DigitalObject> digitalObject = repository
                .findByPidAndRUserId(pid, altoEditorUserId)
                .stream()
                .findFirst();

        if (digitalObject.isEmpty()) {
            // TODO: specific exception
            throw new RuntimeException("Original ALTO not found for PID: " + pid);
        }

        DigitalObject obj = digitalObject.get();
        String version = obj.getVersion();

        String content = akubraService.getDatastreamContent(
                obj.getPid(),
                Datastream.ALTO,
                version);

        return new DigitalObjectWithContent(obj, content);
    }

    private DigitalObjectWithContent createNewAltoVersion(String pid, Integer userId, String altoContent) {
        Optional<DigitalObject> objOpt = repository.findWithMaxVersionByPid(pid)
                .stream()
                .findFirst();

        if (objOpt.isEmpty()) {
            // TODO: specific exception
            throw new RuntimeException("Digital object not found for PID: " + pid);
        }

        String newVersion = objOpt.get().getNextVersion();

        akubraService.saveAltoContent(
                pid,
                newVersion,
                altoContent.getBytes());

        DigitalObject newDigitalObject = repository.save(DigitalObject.builder()
                .rUserId(objOpt.get().getRUserId())
                .instance(objOpt.get().getInstance())
                .pid(pid)
                .version(newVersion)
                .state(DigitalObjectState.EDITED)
                .build());

        return new DigitalObjectWithContent(newDigitalObject, altoContent);
    }

    private DigitalObjectWithContent updateAltoVersion(String pid, Integer userId, String altoContent) {
        Optional<DigitalObject> objOpt = repository.findUpdateCandidate(
                pid,
                userId,
                getUserIdByUsername(USER_ALTOEDITOR));

        if (objOpt.isEmpty()) {
            // TODO: specific exception
            throw new RuntimeException("No ALTO version available for update for PID: " + pid);
        }

        akubraService.saveAltoContent(
                pid,
                objOpt.get().getVersion(),
                altoContent.getBytes());
        
        DigitalObject updatedDigitalObject = objOpt.get();
        updatedDigitalObject.setState(DigitalObjectState.EDITED);
        updatedDigitalObject.setDate(LocalDateTime.now());
        repository.save(updatedDigitalObject);

        return new DigitalObjectWithContent(updatedDigitalObject, altoContent);
    }

    public DigitalObjectWithContent updateOrCreateAlto(String pid, Integer userId, String altoContent) {
        Optional<DigitalObject> objOpt = repository.findByPidAndRUserId(pid, userId);

        if (objOpt.isEmpty()) {
            return createNewAltoVersion(pid, userId, altoContent);
        }

        if (List.of(
                DigitalObjectState.ACCEPTED,
                DigitalObjectState.REJECTED,
                DigitalObjectState.UPLOADED).contains(objOpt.get().getState())) {
            return createNewAltoVersion(pid, userId, altoContent);
        }

        return updateAltoVersion(pid, userId, altoContent);
    }

    public String getOcr(String pid, String version, Integer userId) {
        Optional<DigitalObject> digitalObject = repository
                .findByPidAndRUserIdAndVersion(pid, userId, version)
                .stream()
                .findFirst();

        if (digitalObject.isEmpty()) {
            // TODO: specific exception
            throw new RuntimeException("Digital object not found for PID: " + pid);
        }

        String content = akubraService.getDatastreamContent(
                digitalObject.get().getPid(),
                Datastream.ALTO,
                digitalObject.get().getVersion());

        return altoOcrService.convertAltoToOcr(content);
    }

    public byte[] getKrameriusObjectImage(String pid, String instanceId, String token) {
        if (instanceId == null) {
            Optional<DigitalObject> obj = repository.findByPid(pid).stream().findFirst();

            if (obj.isEmpty()) {
                throw new RuntimeException("Digital object not found for PID: " + pid);
            }

            instanceId = obj.get().getInstance();
        }

        return krameriusService.getImageBytes(pid, instanceId, token);
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
                obj.getInstance(),
                akubraService.getDatastreamContent(
                        obj.getPid(),
                        Datastream.ALTO,
                        obj.getVersion()),
                akubraService.getDatastreamContent(
                        obj.getPid(),
                        Datastream.OCR,
                        obj.getVersion()));
    }
}