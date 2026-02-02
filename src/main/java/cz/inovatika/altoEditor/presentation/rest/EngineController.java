package cz.inovatika.altoEditor.presentation.rest;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cz.inovatika.altoEditor.presentation.dto.request.EngineSearchRequest;
import cz.inovatika.altoEditor.presentation.dto.response.EngineDto;
import cz.inovatika.altoEditor.presentation.facade.EngineFacade;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/engines")
@RequiredArgsConstructor
public class EngineController {
    
    private final EngineFacade facade;

    /**
     * Search engines with optional filtering by enabled status and pagination.
     */
    @GetMapping
    @PreAuthorize("hasAuthority('EDITOR') or hasAuthority('CURATOR')")
    public ResponseEntity<Page<EngineDto>> getEngines(
            @ModelAttribute EngineSearchRequest request,
            Pageable pageable) {

        Page<EngineDto> page = facade.searchEngines(request, pageable);

        return ResponseEntity.ok(page);
    }
}
