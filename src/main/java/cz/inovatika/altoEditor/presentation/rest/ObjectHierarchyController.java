package cz.inovatika.altoEditor.presentation.rest;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import cz.inovatika.altoEditor.application.facade.KrameriusFacade;
import cz.inovatika.altoEditor.application.facade.ObjectHierarchyFacade;
import cz.inovatika.altoEditor.domain.enums.BatchPriority;
import cz.inovatika.altoEditor.presentation.dto.request.ObjectHierarchySearchRequest;
import cz.inovatika.altoEditor.presentation.dto.response.BatchDto;
import cz.inovatika.altoEditor.presentation.dto.response.ObjectHierarchyNodeDto;

import lombok.RequiredArgsConstructor;
@RestController
@RequestMapping("/api/object-hierarchy")
@RequiredArgsConstructor
public class ObjectHierarchyController {

    private final ObjectHierarchyFacade facade;

    private final KrameriusFacade krameriusFacade;
    
    @GetMapping("/search")
    @PreAuthorize("hasAuthority('CURATOR')")
    public ResponseEntity<Page<ObjectHierarchyNodeDto>> search(
            @ModelAttribute ObjectHierarchySearchRequest request,
            Pageable pageable) {

        Page<ObjectHierarchyNodeDto> page = facade.search(request, pageable);

        return ResponseEntity.ok(page);
    }

    @GetMapping("/{pid}/from-kramerius")
    @PreAuthorize("hasAuthority('CURATOR')")
    public ResponseEntity<ObjectHierarchyNodeDto> getByPid(
            @PathVariable String pid,
            @RequestParam(required = false) String instanceId) {

        ObjectHierarchyNodeDto node = krameriusFacade.getHierarchyNode(pid, instanceId);

        return ResponseEntity.ok(node);
    }

    @PostMapping("/{pid}/generate")
    @PreAuthorize("hasAuthority('CURATOR')")
    public ResponseEntity<BatchDto> generateAlto(
            @PathVariable String pid,
            @RequestParam(required = false) BatchPriority priority) {

        BatchDto batch = facade.generateAlto(pid, priority);

        return ResponseEntity.ok(batch);
    }

    @PostMapping("/{pid}/fetch")
    @PreAuthorize("hasAuthority('CURATOR')")
    public ResponseEntity<BatchDto> fetchFromKramerius(
            @PathVariable String pid,
            @RequestParam(required = false) BatchPriority priority) {

        BatchDto batch = facade.fetchFromKramerius(pid, priority);

        return ResponseEntity.ok(batch);
    }
}
