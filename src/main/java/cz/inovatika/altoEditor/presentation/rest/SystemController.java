package cz.inovatika.altoEditor.presentation.rest;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cz.inovatika.altoEditor.presentation.dto.response.InfoDto;
import lombok.RequiredArgsConstructor;

/**
 * REST API for system-level information (e.g. application version).
 */
@RestController
@RequestMapping("/api/system")
@RequiredArgsConstructor
public class SystemController {

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
}
