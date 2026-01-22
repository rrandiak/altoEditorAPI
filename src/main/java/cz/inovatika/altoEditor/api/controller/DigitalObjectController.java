package cz.inovatika.altoEditor.api.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import cz.inovatika.altoEditor.api.dto.DigitalObjectAltoDto;
import cz.inovatika.altoEditor.api.dto.DigitalObjectDto;
import cz.inovatika.altoEditor.api.dto.DigitalObjectSearchRequest;
import cz.inovatika.altoEditor.api.facade.DigitalObjectFacade;
import cz.inovatika.altoEditor.core.enums.BatchPriority;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/objects")
@RequiredArgsConstructor
public class DigitalObjectController {

    private final DigitalObjectFacade facade;

    @GetMapping
    @PreAuthorize("hasAuthority('EDITOR') or hasAuthority('CURATOR')")
    public ResponseEntity<Page<DigitalObjectDto>> getDigitalObjects(
            @ModelAttribute DigitalObjectSearchRequest request,
            Pageable pageable) {

        Page<DigitalObjectDto> page = facade.searchDigitalObjects(request, pageable);

        return ResponseEntity.ok(page);
    }

    @GetMapping("/{pid}/alto")
    @PreAuthorize("hasAuthority('EDITOR') or hasAuthority('CURATOR')")
    public ResponseEntity<DigitalObjectAltoDto> getDigitalObjectAlto(
            @PathVariable String pid,
            @RequestParam(required = false) String instanceId,
            @RequestParam(required = false) String version) {

        DigitalObjectAltoDto altoContent = facade.getDigitalObjectAlto(pid, instanceId, version);

        return ResponseEntity.ok(altoContent);
    }

    @GetMapping("/{pid}/original-alto")
    @PreAuthorize("hasAuthority('EDITOR') or hasAuthority('CURATOR')")
    public ResponseEntity<DigitalObjectAltoDto> getDigitalObjectAltoOriginal(
            @PathVariable String pid) {

        DigitalObjectAltoDto altoOriginalContent = facade.getDigitalObjectOriginalAlto(pid);

        return ResponseEntity.ok(altoOriginalContent);
    }

    @PostMapping("/{pid}/alto")
    @PreAuthorize("hasAuthority('EDITOR') or hasAuthority('CURATOR')")
    public ResponseEntity<String> newAltoVersion(
            @PathVariable String pid,
            @RequestBody String altoContent) {

        String result = facade.createNewAltoVersion(pid, altoContent);

        return ResponseEntity.ok(result);
    }

    @GetMapping("/{pid}/ocr")
    @PreAuthorize("hasAuthority('EDITOR') or hasAuthority('CURATOR')")
    public ResponseEntity<String> getDigitalObjectOcr(
            @PathVariable String pid,
            @RequestParam(required = false) String version) {

        String ocrContent = facade.getDigitalObjectOcr(pid, version);

        return ResponseEntity.ok(ocrContent);
    }

    @GetMapping("/{pid}/image")
    @PreAuthorize("hasAuthority('EDITOR') or hasAuthority('CURATOR')")
    public ResponseEntity<byte[]> getKrameriusObjectImage(
            @PathVariable String pid,
            @RequestParam(required = false) String instanceId) {

        byte[] imageData = facade.getKrameriusObjectImage(pid, instanceId);

        return ResponseEntity.ok(imageData);
    }

    @PostMapping("/{pid}/generate")
    @PreAuthorize("hasAuthority('CURATOR')")
    public ResponseEntity<String> generateAlto(
            @PathVariable String pid,
            @RequestParam(required = false) BatchPriority priority) {

        String result = facade.generateAlto(pid, priority);

        return ResponseEntity.ok(result);
    }

    @PostMapping("/{objectId}/accept")
    @PreAuthorize("hasAuthority('CURATOR')")
    public ResponseEntity<Void> acceptDigitalObject(
            @PathVariable int objectId) {

        facade.acceptDigitalObject(objectId);

        return ResponseEntity.ok().build();
    }

    @PostMapping("/{objectId}/reject")
    @PreAuthorize("hasAuthority('CURATOR')")
    public ResponseEntity<Void> rejectDigitalObject(
            @PathVariable int objectId) {

        facade.rejectDigitalObject(objectId);

        return ResponseEntity.ok().build();

    }

    @PostMapping("/{pid}/lock")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> lockDigitalObject(
            @PathVariable String pid) {
        return ResponseEntity.internalServerError().body("Locking digital objects is not implemented yet.");
    }

    @PostMapping("/{pid}/unlock")
    public ResponseEntity<String> unlockDigitalObject(
            @PathVariable String pid) {
        return ResponseEntity.internalServerError().body("Unlocking digital objects is not implemented yet.");
    }

}