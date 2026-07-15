package cz.inovatika.altoEditor.presentation.dto.request;

import java.util.List;

import cz.inovatika.altoEditor.domain.enums.BatchPriority;
import cz.inovatika.altoEditor.domain.enums.HierarchyGenerateScope;
import cz.inovatika.altoEditor.domain.enums.PipelineStage;
import lombok.Data;

/**
 * Request to start a unified load→generate→accept pipeline for one hierarchy.
 * {@code stages} selects which stages run (canonical order enforced server-side).
 */
@Data
public class PipelineRequest {

    /** Hierarchy PID to process (e.g. a periodical item). */
    private String pid;

    /** Kramerius instance; defaults to the configured default when omitted. */
    private String instance;

    /** OCR engine; required when GENERATE or ACCEPT is included. */
    private String engine;

    /** Generate scope; defaults to ALL. Only used when GENERATE is included. */
    private HierarchyGenerateScope scope;

    /** Stages to run (subset of RETRIEVE, GENERATE, ACCEPT). */
    private List<PipelineStage> stages;

    /** Batch priority; defaults to MEDIUM. */
    private BatchPriority priority;
}
