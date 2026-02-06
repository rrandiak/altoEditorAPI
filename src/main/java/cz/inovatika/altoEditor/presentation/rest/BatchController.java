package cz.inovatika.altoEditor.presentation.rest;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cz.inovatika.altoEditor.presentation.dto.response.BatchDto;
import cz.inovatika.altoEditor.presentation.facade.BatchFacade;
import cz.inovatika.altoEditor.presentation.dto.request.BatchSearchRequest;
import lombok.RequiredArgsConstructor;

/**
 * REST API for batch jobs (ALTO generation, hierarchy retrieval, etc.).
 * Batches are created via hierarchy or ALTO-version endpoints; this API allows listing and filtering them.
 *
 * TODO: rename to JobController and all related classes to Job*
 */
@RestController
@RequestMapping("/api/batches")
@RequiredArgsConstructor
public class BatchController {

    private final BatchFacade facade;

    /**
     * Search batches with optional filters and pagination.
     *
     * @param request  Optional filters (PID, state, substate, date range, priority, type, instance).
     * @param pageable Standard Spring pagination (page, size, sort).
     * @return Paginated list of batch DTOs.
     */
    @GetMapping
    @PreAuthorize("hasAuthority('CURATOR')")
    public ResponseEntity<Page<BatchDto>> getBatches(
            @ModelAttribute BatchSearchRequest request,
            Pageable pageable) {

        Page<BatchDto> page = facade.searchBatches(request, pageable);

        return ResponseEntity.ok(page);
    }
}
