package cz.inovatika.altoEditor.presentation.rest;

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
import cz.inovatika.altoEditor.infrastructure.kramerius.model.KrameriusObjectMetadata;
import cz.inovatika.altoEditor.presentation.dto.request.ObjectHierarchySearchRequest;
import cz.inovatika.altoEditor.presentation.dto.response.BatchDto;
import cz.inovatika.altoEditor.presentation.dto.response.HierarchySearchDto;
import cz.inovatika.altoEditor.presentation.dto.response.SearchResultsDto;
import cz.inovatika.altoEditor.presentation.facade.KrameriusFacade;
import cz.inovatika.altoEditor.presentation.facade.ObjectHierarchyFacade;
import lombok.RequiredArgsConstructor;
@RestController
@RequestMapping("/api/hierarchy")
@RequiredArgsConstructor
public class ObjectHierarchyController {

    private final ObjectHierarchyFacade facade;

    private final KrameriusFacade krameriusFacade;
    
    /**
     * Search object hierarchy nodes with optional filters and pagination.
     */
    @GetMapping("/search")
    @PreAuthorize("hasAuthority('CURATOR')")
    public ResponseEntity<SearchResultsDto<HierarchySearchDto>> search(
            @ModelAttribute ObjectHierarchySearchRequest request) {

        SearchResultsDto<HierarchySearchDto> result = facade.search(request);

        return ResponseEntity.ok(result);
    }

    /**
     * Get object metadata from Kramerius by PID.
     */
    @GetMapping("/{pid}/from-kramerius")
    @PreAuthorize("hasAuthority('CURATOR')")
    public ResponseEntity<KrameriusObjectMetadata> getByPid(
            @PathVariable String pid,
            @RequestParam(required = false) String instanceId) {

        KrameriusObjectMetadata node = krameriusFacade.getObjectMetadata(pid, instanceId);

        return ResponseEntity.ok(node);
    }

    @PostMapping("/{pid}/generate-alto")
    @PreAuthorize("hasAuthority('CURATOR')")
    public ResponseEntity<BatchDto> generateAlto(
            @PathVariable String pid,
            @RequestParam(required = false) BatchPriority priority) {

        BatchDto batch = facade.generateAlto(pid, priority);

        return ResponseEntity.ok(batch);
    }
    
    @PostMapping("/{pid}/fetch-from-kramerius")
    @PreAuthorize("hasAuthority('CURATOR')")
    public ResponseEntity<BatchDto> fetchFromKramerius(
            @PathVariable String pid,
            @RequestParam(required = false) BatchPriority priority) {

        BatchDto batch = facade.fetchFromKramerius(pid, priority);

        return ResponseEntity.ok(batch);
    }
}
