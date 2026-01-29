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

import cz.inovatika.altoEditor.domain.enums.BatchPriority;
import cz.inovatika.altoEditor.presentation.dto.response.BatchDto;
import cz.inovatika.altoEditor.presentation.dto.response.DigitalObjectDto;
import cz.inovatika.altoEditor.presentation.facade.DigitalObjectFacade;
import cz.inovatika.altoEditor.presentation.dto.request.DigitalObjectSearchRequest;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/objects")
@RequiredArgsConstructor
public class DigitalObjectController {

    private final DigitalObjectFacade facade;

    /**
     * Search digital objects related to the current user with optional filters and pagination.
     * Related digital objects are those associated with the currently authenticated user
     * and digital objects in 'ACTIVE' state.
     * 
     * Search criteria are restricted to ensure that only digital objects linked to the user
     * and digital objects in 'ACTIVE' state are returned.
     * 
     * @param request Search criteria for digital objects.
     * @param pageable Pagination information.
     * 
     * @return A paginated list of digital objects matching the search criteria.
     */
    @GetMapping("/search-related")
    @PreAuthorize("hasAuthority('EDITOR') or hasAuthority('CURATOR')")
    public ResponseEntity<Page<DigitalObjectDto>> getUserDigitalObjects(
            @ModelAttribute DigitalObjectSearchRequest request,
            Pageable pageable) {

        Page<DigitalObjectDto> page = facade.searchRelated(request, pageable);

        return ResponseEntity.ok(page);
    }

    /**
     * Search all digital objects with optional filters and pagination.
     * 
     * @param request Search criteria for digital objects.
     * @param pageable Pagination information.
     * 
     * @return A paginated list of digital objects matching the search criteria.
     */
    @GetMapping("/search-all")
    @PreAuthorize("hasAuthority('CURATOR')")
    public ResponseEntity<Page<DigitalObjectDto>> getDigitalObjects(
            @ModelAttribute DigitalObjectSearchRequest request,
            Pageable pageable) {

        Page<DigitalObjectDto> page = facade.searchAll(request, pageable);

        return ResponseEntity.ok(page);
    }

    /**
     * Get ALTO content of a digital object related to the current user.
     * 
     * The ALTO content is retrieved in the following order:
     * 1. The version owned by the current user.
     * 2. The version currently in 'ACTIVE' state.
     * 3. If no ALTO is found, a new ALTO is fetched from Kramerius
     *    using the provided instance ID and this ALTO is then returned.
     * 
     * Instance ID is used when fetching a new ALTO from Kramerius if no existing ALTO is found.
     * If instance ID is not provided and a new ALTO needs to be fetched,
     * default Kramerius instance will be used.
     * 
     * @param pid The identifier of the digital object.
     * @param instanceId The instance ID of the digital object (optional).
     * 
     * @return The ALTO content of the digital object.
     */
    @GetMapping("/{pid}/related-alto")
    @PreAuthorize("hasAuthority('EDITOR') or hasAuthority('CURATOR')")
    public ResponseEntity<DigitalObjectDto> getRelatedAlto(
            @PathVariable String pid,
            @RequestParam(required = false) String instanceId) {

        DigitalObjectDto altoContent = facade.getRelatedAlto(pid, instanceId);

        return ResponseEntity.ok(altoContent);
    }

    /**
     * Get ALTO content of a specific version of a digital object.
     * 
     * @param pid The identifier of the digital object.
     * @param version The version number of the ALTO content to retrieve.
     * 
     * @return The ALTO content of the specified version of the digital object.
     */
    @GetMapping("/{pid}/alto-version/{version}")
    @PreAuthorize("hasAuthority('CURATOR')")
    public ResponseEntity<DigitalObjectDto> getAltoVersion(
            @PathVariable String pid,
            @PathVariable Integer version) {

        DigitalObjectDto altoContent = facade.getAltoVersion(pid, version);

        return ResponseEntity.ok(altoContent);
    }

    /**
     * Get the ALTO content of active version of a digital object.
     * 
     * @param pid The identifier of the digital object.
     * 
     * @return The ALTO content of the active version of the digital object.
     */
    @GetMapping("/{pid}/active-alto")
    @PreAuthorize("hasAuthority('EDITOR') or hasAuthority('CURATOR')")
    public ResponseEntity<DigitalObjectDto> getActiveAlto(
            @PathVariable String pid) {

        DigitalObjectDto altoOriginalContent = facade.getActiveAlto(pid);

        return ResponseEntity.ok(altoOriginalContent);
    }

    /**
     * Create a new version or replace pending version of ALTO content
     * for a digital object and current user.
     * 
     * @param pid The identifier of the digital object.
     * @param altoContent The new ALTO content to be saved.
     * 
     * @return The updated digital object with the new ALTO version.
     */
    @PostMapping("/{pid}/alto")
    @PreAuthorize("hasAuthority('EDITOR') or hasAuthority('CURATOR')")
    public ResponseEntity<DigitalObjectDto> newAltoVersion(
            @PathVariable String pid,
            @RequestBody String altoContent) {

        DigitalObjectDto result = facade.createNewAltoVersion(pid, altoContent);

        return ResponseEntity.ok(result);
    }

    /**
     * Get OCR text content of a digital object for the current user.
     * 
     * @param objectId The identifier of the digital object.
     * 
     * @return The OCR text content of the digital object.
     */
    @GetMapping("/{objectId}/ocr")
    @PreAuthorize("hasAuthority('EDITOR') or hasAuthority('CURATOR')")
    public ResponseEntity<String> getDigitalObjectOcr(
            @PathVariable Integer objectId) {

        String ocrContent = facade.getOcr(objectId);

        return ResponseEntity.ok(ocrContent);
    }

    /**
     * Get image for a digital object.
     * 
     * @param pid The identifier of the digital object.
     * @param instanceId The instance ID of the digital object (optional).
     * 
     * @return The image bytes of the digital object.
     */
    @GetMapping("/{pid}/image")
    @PreAuthorize("hasAuthority('EDITOR') or hasAuthority('CURATOR')")
    public ResponseEntity<byte[]> getImage(
            @PathVariable String pid,
            @RequestParam(required = false) String instanceId) {

        byte[] imageData = facade.getImage(pid, instanceId);

        return ResponseEntity.ok(imageData);
    }

    /**
     * Generate ALTO for a digital object by creating a batch process.
     * The PID can target either a single specific digital object or a document hierarchy.
     * 
     * @param pid The identifier of the digital object or document hierarchy.
     * @param priority The priority of the batch process (optional).
     * 
     * @return The created batch process information.
     */
    @PostMapping("/{pid}/generate")
    @PreAuthorize("hasAuthority('EDITOR') or hasAuthority('CURATOR')")
    public ResponseEntity<BatchDto> generateAlto(
            @PathVariable String pid,
            @RequestParam(required = false) BatchPriority priority) {

        BatchDto result = facade.generateAlto(pid, priority);

        return ResponseEntity.ok(result);
    }

    /**
     * Set the specified digital object as the ACTIVE version for its PID and instance.
     * This operation:
     * - Changes the object's state to 'ACTIVE' (making it the default for editing and viewing).
     * - Uploads the related ALTO and OCR content to Kramerius for live use.
     * - Archives any previously ACTIVE version for the same PID/instance.
     *
     * State transition: PENDING -> ACTIVE (previous ACTIVE becomes ARCHIVED)
     *
     * @param objectId The ID of the digital object to activate.
     * 
     * @return HTTP 200 OK if successful.
     */
    @PostMapping("/{objectId}/set-active")
    @PreAuthorize("hasAuthority('CURATOR')")
    public ResponseEntity<Void> setActive(
            @PathVariable int objectId) {

        facade.setActive(objectId);

        return ResponseEntity.ok().build();
    }

    /**
     * Reject a digital object by its ID.
     * State transition: PENDING -> REJECTED
     *
     * @param objectId The ID of the digital object to be rejected.
     * 
     * @return HTTP 200 OK if successful.
     */
    @PostMapping("/{objectId}/reject")
    @PreAuthorize("hasAuthority('CURATOR')")
    public ResponseEntity<Void> reject(
            @PathVariable int objectId) {

        facade.reject(objectId);

        return ResponseEntity.ok().build();

    }

    /**
     * Archive a digital object by its ID.
     * State transition: ACTIVE or PENDING -> ARCHIVED
     *
     * @param objectId The ID of the digital object to be archived.
     * 
     * @return HTTP 200 OK if successful.
     */
    @PostMapping("/{objectId}/archive")
    @PreAuthorize("hasAuthority('CURATOR')")
    public ResponseEntity<Void> archive(
            @PathVariable int objectId) {

        facade.archive(objectId);

        return ResponseEntity.ok().build();

    }
}