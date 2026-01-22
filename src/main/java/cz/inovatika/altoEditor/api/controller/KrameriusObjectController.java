package cz.inovatika.altoEditor.api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import cz.inovatika.altoEditor.api.facade.KrameriusObjectFacade;
import cz.inovatika.altoEditor.kramerius.domain.KrameriusObjectMetadataDto;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/kramerius")
@RequiredArgsConstructor
public class KrameriusObjectController {

    private final KrameriusObjectFacade facade;

    @GetMapping("/{pid}")
    @PreAuthorize("hasAuthority('EDITOR') or hasAuthority('CURATOR')")
    public ResponseEntity<KrameriusObjectMetadataDto> getKrameriusObject(
            @PathVariable String pid,
            @RequestParam(required = false) String instanceId) {

        KrameriusObjectMetadataDto krameriusObject = facade.getKrameriusObject(pid, instanceId);

        return ResponseEntity.ok(krameriusObject);
    }

    @PostMapping("/{objectId}/upload")
    @PreAuthorize("hasAuthority('EDITOR') or hasAuthority('CURATOR')")
    public ResponseEntity<Void> uploadObjectToKramerius(
            @PathVariable int objectId) {

        facade.uploadObjectToKramerius(objectId);

        return ResponseEntity.ok().build();
    }

}