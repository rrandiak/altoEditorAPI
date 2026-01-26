package cz.inovatika.altoEditor.presentation.rest;

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

import cz.inovatika.altoEditor.application.facade.DigitalObjectFacade;
import cz.inovatika.altoEditor.domain.enums.BatchPriority;
import cz.inovatika.altoEditor.presentation.dto.response.BatchDto;
import cz.inovatika.altoEditor.presentation.dto.response.DigitalObjectDto;
import cz.inovatika.altoEditor.presentation.dto.request.DigitalObjectSearchRequest;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/objects")
@RequiredArgsConstructor
public class DigitalObjectController {

    private final DigitalObjectFacade facade;

    @GetMapping("/related")
    @PreAuthorize("hasAuthority('EDITOR') or hasAuthority('CURATOR')")
    public ResponseEntity<Page<DigitalObjectDto>> getUserDigitalObjects(
            @ModelAttribute DigitalObjectSearchRequest request,
            Pageable pageable) {

        Page<DigitalObjectDto> page = facade.searchRelatedDigitalObjects(request, pageable);

        return ResponseEntity.ok(page);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('CURATOR')")
    public ResponseEntity<Page<DigitalObjectDto>> getDigitalObjects(
            @ModelAttribute DigitalObjectSearchRequest request,
            Pageable pageable) {

        Page<DigitalObjectDto> page = facade.searchDigitalObjects(request, pageable);

        return ResponseEntity.ok(page);
    }

    @GetMapping("/{pid}/alto")
    @PreAuthorize("hasAuthority('EDITOR') or hasAuthority('CURATOR')")
    public ResponseEntity<DigitalObjectDto> getDigitalObjectAlto(
            @PathVariable String pid,
            @RequestParam(required = false) String instanceId,
            @RequestParam(required = false) Integer version) {

        DigitalObjectDto altoContent = facade.getDigitalObjectAlto(pid, instanceId, version);

        return ResponseEntity.ok(altoContent);
    }

    @GetMapping("/{pid}/original-alto")
    @PreAuthorize("hasAuthority('EDITOR') or hasAuthority('CURATOR')")
    public ResponseEntity<DigitalObjectDto> getDigitalObjectAltoOriginal(
            @PathVariable String pid) {

        DigitalObjectDto altoOriginalContent = facade.getDigitalObjectOriginalAlto(pid);

        return ResponseEntity.ok(altoOriginalContent);
    }

    @PostMapping("/{pid}/alto")
    @PreAuthorize("hasAuthority('EDITOR') or hasAuthority('CURATOR')")
    public ResponseEntity<DigitalObjectDto> newAltoVersion(
            @PathVariable String pid,
            @RequestBody String altoContent) {

        DigitalObjectDto result = facade.createNewAltoVersion(pid, altoContent);

        return ResponseEntity.ok(result);
    }

    @GetMapping("/{pid}/ocr")
    @PreAuthorize("hasAuthority('EDITOR') or hasAuthority('CURATOR')")
    public ResponseEntity<String> getDigitalObjectOcr(
            @PathVariable String pid,
            @RequestParam(required = false) Integer version) {

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
    public ResponseEntity<BatchDto> generateAlto(
            @PathVariable String pid,
            @RequestParam(required = false) BatchPriority priority) {

        BatchDto result = facade.generateAlto(pid, priority);

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
    @PreAuthorize("hasRole('CURATOR')")
    public ResponseEntity<String> lockDigitalObject(
            @PathVariable String pid) {
        return ResponseEntity.internalServerError().body("Locking digital objects is not implemented yet.");
    }

    @PostMapping("/{pid}/unlock")
    @PreAuthorize("hasRole('CURATOR')")
    public ResponseEntity<String> unlockDigitalObject(
            @PathVariable String pid) {
        return ResponseEntity.internalServerError().body("Unlocking digital objects is not implemented yet.");
    }

}