package cz.inovatika.altoEditor.domain.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cz.inovatika.altoEditor.domain.enums.Datastream;
import cz.inovatika.altoEditor.domain.enums.DigitalObjectState;
import cz.inovatika.altoEditor.domain.enums.SpecialUser;
import cz.inovatika.altoEditor.domain.model.DigitalObject;
import cz.inovatika.altoEditor.domain.repository.DigitalObjectRepository;
import cz.inovatika.altoEditor.domain.repository.UserRepository;
import cz.inovatika.altoEditor.domain.repository.spec.DigitalObjectSpecifications;
import cz.inovatika.altoEditor.domain.service.container.DigitalObjectUploadContent;
import cz.inovatika.altoEditor.domain.service.container.DigitalObjectWithContent;
import cz.inovatika.altoEditor.infrastructure.editor.AltoXmlService;
import cz.inovatika.altoEditor.infrastructure.kramerius.KrameriusService;
import cz.inovatika.altoEditor.infrastructure.storage.AkubraService;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class DigitalObjectService {

    private final DigitalObjectRepository repository;

    private final UserService userService;

    private final UserRepository userRepository;

    private final AkubraService akubraService;

    private final KrameriusService krameriusService;

    private final AltoXmlService altoXmlService;

    @Transactional(readOnly = true)
    public Page<DigitalObject> search(
            String pid,
            String username,
            Pageable pageable) {
        Specification<DigitalObject> spec = Specification.allOf(
                DigitalObjectSpecifications.hasPid(pid),
                DigitalObjectSpecifications
                        .hasUser(userRepository.findByLogin(username).map(user -> user.getId()).orElse(null)));

        return repository.findAll(spec, pageable);
    }

    public DigitalObjectWithContent findAlto(String pid, Integer version, Integer userId) {
        // Search sequence for a given PID
        // 1. By specific version
        // 2. By user
        // 3. Default version from PERO
        // 4. Default version from Kramerius
        Optional<DigitalObject> digitalObject = repository
                .findByPidAndVersionAndUsersWithPriority(pid, version, userId,
                        userService.getSpecialUser(SpecialUser.PERO).getId(),
                        userService.getSpecialUser(SpecialUser.ALTOEDITOR).getId())
                .stream()
                .findFirst();

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
            throw new RuntimeException("Digital object with PID already exists: " + pid);
        }

        byte[] foxml = krameriusService.getFoxmlBytes(pid, instanceId, token);

        byte[] alto = akubraService.getLatestDsVersionBinaryContent(foxml, Datastream.ALTO);

        if (alto == null) {
            throw new RuntimeException("ALTO datastream not found in FOXML for PID: " + pid);
        }

        Optional<DigitalObject> maxVersionObj = repository.findFirstByPidOrderByVersionDesc(pid);

        DigitalObject obj = repository.save(
                DigitalObject.builder().userId(userId).instanceId(instanceId).pid(pid)
                        .version(maxVersionObj.map(DigitalObject::getVersion).orElse(0)).build());

        byte[] content = akubraService.retrieveDsBinaryContent(
                obj.getPid(),
                Datastream.ALTO,
                obj.getVersion());

        return new DigitalObjectWithContent(obj, content);
    }

    public DigitalObjectWithContent getOriginalAlto(String pid) {
        Optional<DigitalObject> digitalObject = repository
                .findByPidAndUserId(pid, userService.getSpecialUser(SpecialUser.ALTOEDITOR).getId())
                .stream()
                .findFirst();

        if (digitalObject.isEmpty()) {
            // TODO: specific exception
            throw new RuntimeException("Original ALTO not found for PID: " + pid);
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
            // TODO: specific exception
            throw new RuntimeException("Digital object not found for PID: " + pid);
        }

        DigitalObject obj = objOpt.get();
        int newVersion = obj.getVersion() + 1;

        akubraService.saveAltoContent(
                pid,
                newVersion,
                altoContent.getBytes());

        DigitalObject newDigitalObject = repository.save(DigitalObject.builder()
                .userId(obj.getUserId())
                .instanceId(obj.getInstanceId())
                .pid(pid)
                .version(newVersion)
                .state(DigitalObjectState.EDITED)
                .build());

        return new DigitalObjectWithContent(newDigitalObject, altoContent.getBytes());
    }

    private DigitalObjectWithContent updateAltoVersion(String pid, Integer userId, String altoContent) {
        Optional<DigitalObject> objOpt = repository.findUpdateCandidate(
                pid,
                userId,
                userService.getSpecialUser(SpecialUser.ALTOEDITOR).getId());

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

        return new DigitalObjectWithContent(updatedDigitalObject, altoContent.getBytes());
    }

    public DigitalObjectWithContent updateOrCreateAlto(String pid, Integer userId, String altoContent) {
        Optional<DigitalObject> objOpt = repository.findByPidAndUserId(pid, userId);

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

    public String getOcr(String pid, Integer version, Integer userId) {
        DigitalObjectWithContent digitalObjectWithContent = this.findAlto(pid, version, userId);

        if (digitalObjectWithContent == null) {
            // TODO: specific exception
            throw new RuntimeException("Digital object not found for PID: " + pid);
        }

        return altoXmlService.convertAltoToOcr(digitalObjectWithContent.getContent());
    }

    public byte[] getKrameriusObjectImage(String pid, String instanceId, String token) {
        if (instanceId == null) {
            Optional<DigitalObject> obj = repository.findByPid(pid).stream().findFirst();

            if (obj.isEmpty()) {
                throw new RuntimeException("Digital object not found for PID: " + pid);
            }

            instanceId = obj.get().getInstanceId();
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