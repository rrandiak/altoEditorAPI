package cz.inovatika.altoEditor.api.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cz.inovatika.altoEditor.api.dto.BatchDto;
import cz.inovatika.altoEditor.api.dto.BatchSearchRequest;
import cz.inovatika.altoEditor.api.facade.BatchFacade;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/batches")
@RequiredArgsConstructor
public class BatchController {

    private final BatchFacade facade;

    /**
     * Search batches with optional filters and pagination.
     */
    @GetMapping
    @PreAuthorize("hasAuthority('EDITOR') or hasAuthority('CURATOR')")
    public ResponseEntity<Page<BatchDto>> getBatches(
            @ModelAttribute BatchSearchRequest request,
            Pageable pageable) {

        Page<BatchDto> page = facade.searchBatches(request, pageable);

        return ResponseEntity.ok(page);
    }
}
