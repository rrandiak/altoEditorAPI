package cz.inovatika.altoEditor.presentation.rest;

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
import cz.inovatika.altoEditor.presentation.dto.response.SearchResultsDto;
import cz.inovatika.altoEditor.presentation.dto.response.AltoVersionDto;
import cz.inovatika.altoEditor.presentation.dto.response.AltoVersionSearchDto;
import cz.inovatika.altoEditor.presentation.facade.AltoVersionFacade;
import cz.inovatika.altoEditor.presentation.dto.request.AltoVersionSearchRelatedRequest;
import cz.inovatika.altoEditor.presentation.dto.request.AltoVersionSearchRequest;
import lombok.RequiredArgsConstructor;

/**
 * REST API for ALTO versions: search, get content by page (PID) or version, OCR, images,
 * create/update versions, state transitions (accept/reject/archive), and per-page ALTO generation.
 */
@RestController
@RequestMapping("/api/alto-versions")
@RequiredArgsConstructor
public class AltoVersionController {

    private final AltoVersionFacade facade;

    /**
     * Search all ALTO versions with optional filters and pagination.
     *
     * @param request Search criteria (users, instance, PIDs, title, date range, states)
     *                and pagination (offset, limit) via {@link AltoVersionSearchRequest}.
     * @return Paginated list of ALTO versions matching the criteria.
     */
    @GetMapping("/search")
    @PreAuthorize("hasAuthority('CURATOR')")
    public ResponseEntity<SearchResultsDto<AltoVersionSearchDto>> getAltoVersions(
            @ModelAttribute AltoVersionSearchRequest request) {

        SearchResultsDto<AltoVersionSearchDto> page = facade.searchAll(request);

        return ResponseEntity.ok(page);
    }

    /**
     * Search ALTO versions related to the current user with optional filters and pagination.
     * Returns only ALTO versions linked to the authenticated user or in {@code ACTIVE} state.
     *
     * @param request Search criteria and pagination (offset, limit) via {@link AltoVersionSearchRelatedRequest}.
     * @return Paginated list of matching ALTO versions.
     */
    @GetMapping("/search/related")
    @PreAuthorize("hasAuthority('EDITOR') or hasAuthority('CURATOR')")
    public ResponseEntity<SearchResultsDto<AltoVersionSearchDto>> getUserAltoVersions(
            @ModelAttribute AltoVersionSearchRelatedRequest request) {

        SearchResultsDto<AltoVersionSearchDto> page = facade.searchRelated(request);

        return ResponseEntity.ok(page);
    }

    /**
     * Get ALTO content of the ALTO version related to the current user.
     * 
     * The ALTO content is retrieved in the following order:
     * 1. The version owned by the current user.
     * 2. The version currently in 'ACTIVE' state.
     * 3. If no ALTO is found, a new ALTO is fetched from Kramerius
     * using the provided instance ID and this ALTO is then returned.
     * 
     * Instance ID is used when fetching a new ALTO from Kramerius if no existing
     * ALTO is found.
     * If instance ID is not provided and a new ALTO needs to be fetched,
     * default Kramerius instance will be used.
     * 
     * @param pid        The identifier of the ALTO version.
     * @param instanceId The instance ID of the ALTO version (optional).
     * 
     * @return The ALTO content of the ALTO version.
     */
    @GetMapping("/{pid}/related")
    @PreAuthorize("hasAuthority('EDITOR') or hasAuthority('CURATOR')")
    public ResponseEntity<AltoVersionDto> getRelatedAlto(
            @PathVariable String pid,
            @RequestParam(required = false) String instanceId) {

        AltoVersionDto altoContent = facade.getRelatedAlto(pid, instanceId);

        return ResponseEntity.ok(altoContent);
    }

    /**
     * Get the currently active ALTO content for a page (PID).
     *
     * @param pid Page identifier
     * @return Active ALTO version DTO including content.
     */
    @GetMapping("/{pid}/active")
    @PreAuthorize("hasAuthority('EDITOR') or hasAuthority('CURATOR')")
    public ResponseEntity<AltoVersionDto> getActiveAlto(
            @PathVariable String pid) {

        AltoVersionDto altoOriginalContent = facade.getActiveAlto(pid);

        return ResponseEntity.ok(altoOriginalContent);
    }

    /**
     * Get a specific revision of ALTO content for a page (PID).
     *
     * @param pid     Page identifier (e.g. {@code uuid:...}).
     * @param version Version number of the ALTO revision.
     * @return ALTO version DTO for that revision.
     */
    @GetMapping("/{pid}/versions/{version}")
    @PreAuthorize("hasAuthority('CURATOR')")
    public ResponseEntity<AltoVersionDto> getAltoVersion(
            @PathVariable String pid,
            @PathVariable Integer version) {

        AltoVersionDto altoContent = facade.getAltoVersion(pid, version);

        return ResponseEntity.ok(altoContent);
    }

    /**
     * Get extracted OCR text content for an ALTO version.
     *
     * @param versionId Database ID of the ALTO version (not the page PID).
     * @return Plain-text OCR content of the ALTO version.
     */
    @GetMapping("/{versionId}/ocr")
    @PreAuthorize("hasAuthority('EDITOR') or hasAuthority('CURATOR')")
    public ResponseEntity<String> getAltoVersionOcr(
            @PathVariable Integer versionId) {

        String ocrContent = facade.getOcr(versionId);

        return ResponseEntity.ok(ocrContent);
    }

    /**
     * Get image for an ALTO version.
     * 
     * @param pid        The identifier of the ALTO version.
     * @param instanceId The instance ID of the ALTO version (optional).
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
     * Create a new version or replace pending version of ALTO content
     * for an ALTO version and current user.
     * 
     * @param pid         The identifier of the ALTO version.
     * @param altoContent The new ALTO content to be saved.
     * 
     * @return The updated ALTO version with the new ALTO version.
     */
    @PostMapping("/{pid}/versions")
    @PreAuthorize("hasAuthority('EDITOR') or hasAuthority('CURATOR')")
    public ResponseEntity<AltoVersionDto> newAltoVersion(
            @PathVariable String pid,
            @RequestBody byte[] altoContent) {

        AltoVersionDto result = facade.updateOrCreateVersion(pid, altoContent);

        return ResponseEntity.ok(result);
    }

    /**
     * Generate ALTO for an ALTO version by creating a batch process.
     * The PID target is single specific Digital Object for which ALTO is generated.
     * 
     * @param pid      The identifier of the ALTO version or document hierarchy.
     * @param priority The priority of the batch process (optional).
     * 
     * @return The created batch process information.
     */
    @PostMapping("/{pid}/generate/{engine}")
    @PreAuthorize("hasAuthority('EDITOR') or hasAuthority('CURATOR')")
    public ResponseEntity<BatchDto> generateAlto(
            @PathVariable String pid,
            @PathVariable String engine,
            @RequestParam(required = false) BatchPriority priority) {

        BatchDto result = facade.generateAlto(pid, engine, priority);

        return ResponseEntity.ok(result);
    }

    /**
     * Set the specified ALTO version as the ACTIVE version for its PID.
     * This operation:
     * - Uploads the related ALTO and OCR content to all Kramerius instances for
     * live use.
     * - Changes the object's state to 'ACTIVE' (making it the default for editing
     * and viewing).
     * - Archives previous ACTIVE version for the same PID, only if the target ALTO
     * version is not already ACTIVE.
     * - Archives all STALE versions of the same PID.
     * Target ALTO version can be in any state.
     * When used on already ACTIVE object, it refreshes content in all Kramerius
     * instances.
     *
     * Permitted state transitions are any state -> ACTIVE.
     *
     * @param versionId The ID of the ALTO version to activate.
     * 
     * @return HTTP 200 OK if successful.
     */
    @PostMapping("/{versionId}/accept")
    @PreAuthorize("hasAuthority('CURATOR')")
    public ResponseEntity<Void> accept(
            @PathVariable int versionId) {

        facade.accept(versionId);

        return ResponseEntity.ok().build();
    }

    /**
     * Reject an ALTO version by its ID.
     * Only permitted state transition is PENDING -> REJECTED
     *
     * @param versionId The ID of the ALTO version to be rejected.
     * 
     * @return HTTP 200 OK if successful.
     */
    @PostMapping("/{versionId}/reject")
    @PreAuthorize("hasAuthority('CURATOR')")
    public ResponseEntity<Void> reject(
            @PathVariable int versionId) {

        facade.reject(versionId);

        return ResponseEntity.ok().build();
    }

    /**
     * Archive an ALTO version by its ID.
     * Only permitted state transition is PENDING -> ARCHIVED
     *
     * @param versionId The ID of the ALTO version to be archived.
     * 
     * @return HTTP 200 OK if successful.
     */
    @PostMapping("/{versionId}/archive")
    @PreAuthorize("hasAuthority('CURATOR')")
    public ResponseEntity<Void> archive(
            @PathVariable int versionId) {

        facade.archive(versionId);

        return ResponseEntity.ok().build();
    }
}