package cz.inovatika.altoEditor.presentation.rest;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cz.inovatika.altoEditor.presentation.dto.request.PipelineRequest;
import cz.inovatika.altoEditor.presentation.dto.response.BatchDto;
import cz.inovatika.altoEditor.presentation.facade.PipelineFacade;
import lombok.RequiredArgsConstructor;

/**
 * REST API for the unified load→generate→accept pipeline. A pipeline is a parent batch that
 * spawns one child stage batch per selected stage; the scheduler runs them in order.
 */
@RestController
@RequestMapping("/api/pipelines")
@RequiredArgsConstructor
public class PipelineController {

    private final PipelineFacade facade;

    /** Start a pipeline; returns the parent batch. */
    @PostMapping
    @PreAuthorize("hasAuthority('CURATOR')")
    public ResponseEntity<BatchDto> create(@RequestBody PipelineRequest request) {
        return ResponseEntity.ok(facade.create(request));
    }

    /** Get a pipeline: the parent batch followed by its child stage batches, ordered by stage. */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('CURATOR')")
    public ResponseEntity<List<BatchDto>> getPipeline(@PathVariable Integer id) {
        return ResponseEntity.ok(facade.getStages(id));
    }
}
