package cz.inovatika.altoEditor.presentation.rest;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import cz.inovatika.altoEditor.domain.enums.BatchPriority;
import cz.inovatika.altoEditor.presentation.dto.response.BatchDto;
import cz.inovatika.altoEditor.presentation.dto.response.InfoDto;
import cz.inovatika.altoEditor.presentation.facade.SystemFacade;
import lombok.RequiredArgsConstructor;

/**
 * REST API for system-level information (e.g. application version).
 */
@RestController
@RequestMapping("/api/system")
@RequiredArgsConstructor
public class SystemController {

    private final SystemFacade facade;

    /**
     * Returns basic system/app information (e.g. API version).
     *
     * @return Info DTO; currently contains {@link InfoDto#getVersion() version}.
     */
    @GetMapping("/info")
    @PreAuthorize("hasAuthority('EDITOR') or hasAuthority('CURATOR')")
    public ResponseEntity<InfoDto> getInfo() {
        return ResponseEntity.ok(new InfoDto());
    }

    /**
     * Create a reindex batch (purge and reindex DigitalObject + AltoVersion search indexes) and submit it.
     * Requires CURATOR.
     *
     * @param priority Priority of the batch.
     * @return The created batch DTO (state PLANNED, then RUNNING when process starts, DONE when finished).
     */
    @PostMapping("/reindex")
    @PreAuthorize("hasAuthority('CURATOR')")
    public ResponseEntity<BatchDto> createReindexBatch(
            @RequestParam(required = false) BatchPriority priority) {

        BatchDto batch = facade.createReindexBatch(priority);

        return ResponseEntity.ok(batch);
    }
}
