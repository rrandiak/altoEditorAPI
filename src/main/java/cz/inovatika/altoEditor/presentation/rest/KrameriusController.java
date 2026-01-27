package cz.inovatika.altoEditor.presentation.rest;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import cz.inovatika.altoEditor.application.facade.KrameriusFacade;
import cz.inovatika.altoEditor.infrastructure.kramerius.model.KrameriusObjectMetadata;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/kramerius")
@RequiredArgsConstructor
public class KrameriusController {

    private final KrameriusFacade facade;

    @GetMapping("/objects/{pid}")
    @PreAuthorize("hasAuthority('EDITOR') or hasAuthority('CURATOR')")
    public ResponseEntity<KrameriusObjectMetadata> getKrameriusObject(
            @PathVariable String pid,
            @RequestParam(required = false) String instanceId) {

        KrameriusObjectMetadata krameriusObject = facade.getKrameriusObject(pid, instanceId);

        return ResponseEntity.ok(krameriusObject);
    }
}