package cz.inovatika.altoEditor.presentation.rest;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import cz.inovatika.altoEditor.domain.enums.BatchPriority;
import cz.inovatika.altoEditor.presentation.dto.request.ObjectHierarchySearchRequest;
import cz.inovatika.altoEditor.presentation.dto.response.BatchDto;
import cz.inovatika.altoEditor.presentation.dto.response.HierarchySearchDto;
import cz.inovatika.altoEditor.presentation.dto.response.KrameriusDigitalObjectDto;
import cz.inovatika.altoEditor.presentation.dto.response.SearchResultsDto;
import cz.inovatika.altoEditor.presentation.facade.ObjectHierarchyFacade;
import lombok.RequiredArgsConstructor;

/**
 * REST API for the digital object hierarchy: search local hierarchy, fetch metadata and children
 * from Kramerius, and trigger batch jobs to fetch hierarchy or generate ALTO for a subtree.
 */
@RestController
@RequestMapping("/api/hierarchy")
@RequiredArgsConstructor
public class ObjectHierarchyController {

    private final ObjectHierarchyFacade facade;

    /**
     * Search object hierarchy nodes (monographs, periods, pages, etc.) with optional filters and pagination.
     *
     * @param request Filters (PID, parent PID, model, title, level) and pagination (offset, limit).
     * @return Search result with total count and list of hierarchy nodes (counts from repository).
     */
    @GetMapping("/search")
    @PreAuthorize("hasAuthority('CURATOR')")
    public ResponseEntity<SearchResultsDto<HierarchySearchDto>> search(
            @ModelAttribute ObjectHierarchySearchRequest request) {

        SearchResultsDto<HierarchySearchDto> result = facade.search(request);

        return ResponseEntity.ok(result);
    }

    /**
     * Get digital object metadata from Kramerius by PID.
     *
     * @param pid        Page or object identifier (e.g. {@code uuid:...}).
     * @param instanceId Optional Kramerius instance ID; default used if omitted.
     * @return Metadata DTO including children count and pages count from Kramerius.
     */
    @GetMapping("/{pid}/from-kramerius")
    @PreAuthorize("hasAuthority('CURATOR')")
    public ResponseEntity<KrameriusDigitalObjectDto> getByPid(
            @PathVariable String pid,
            @RequestParam(required = false) String instanceId) {

        KrameriusDigitalObjectDto node = facade.getObjectMetadata(pid, instanceId);

        return ResponseEntity.ok(node);
    }

    /**
     * Get children metadata from Kramerius for a given object PID.
     *
     * @param pid        Parent object identifier (e.g. {@code uuid:...}).
     * @param instanceId Optional Kramerius instance ID; default used if omitted.
     * @return List of child object metadata DTOs.
     */
    @GetMapping("/{pid}/children-from-kramerius")
    @PreAuthorize("hasAuthority('CURATOR')")
    public ResponseEntity<List<KrameriusDigitalObjectDto>> getChildrenByPid(
            @PathVariable String pid,
            @RequestParam(required = false) String instanceId) {

        List<KrameriusDigitalObjectDto> children = facade.getChildrenMetadata(pid, instanceId);

        return ResponseEntity.ok(children);
    }

    /**
     * Start a batch job to generate ALTO for the hierarchy rooted at the given PID.
     * The batch type is {@code GENERATE_FOR_HIERARCHY}; engine is chosen by the process.
     *
     * @param pid     Root object identifier (e.g. monograph UUID).
     * @param priority Optional batch priority.
     * @return Created batch DTO.
     */
    @PostMapping("/{pid}/generate-alto")
    @PreAuthorize("hasAuthority('CURATOR')")
    public ResponseEntity<BatchDto> generateAlto(
            @PathVariable String pid,
            @RequestParam(required = false) BatchPriority priority) {

        BatchDto batch = facade.generateAlto(pid, priority);

        return ResponseEntity.ok(batch);
    }
    
    /**
     * Start a batch job to fetch the object hierarchy from Kramerius and store it locally.
     * The batch type is {@code RETRIEVE_HIERARCHY}.
     *
     * @param pid     Root or leaf PID to start from (e.g. page or monograph UUID).
     * @param priority Optional batch priority.
     * @return Created batch DTO.
     */
    @PostMapping("/{pid}/fetch-from-kramerius")
    @PreAuthorize("hasAuthority('CURATOR')")
    public ResponseEntity<BatchDto> fetchFromKramerius(
            @PathVariable String pid,
            @RequestParam(required = false) BatchPriority priority) {

        BatchDto batch = facade.fetchFromKramerius(pid, priority);

        return ResponseEntity.ok(batch);
    }
}
