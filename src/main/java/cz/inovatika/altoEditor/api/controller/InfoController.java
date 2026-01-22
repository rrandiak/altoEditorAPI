package cz.inovatika.altoEditor.api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cz.inovatika.altoEditor.api.dto.InfoDto;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/info")
@RequiredArgsConstructor
public class InfoController {

    @GetMapping
    @PreAuthorize("hasAuthority('EDITOR') or hasAuthority('CURATOR')")
    public ResponseEntity<InfoDto> getInfo() {
        return ResponseEntity.ok(new InfoDto());
    }
}
