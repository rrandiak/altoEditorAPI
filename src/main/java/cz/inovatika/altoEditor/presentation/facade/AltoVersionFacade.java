package cz.inovatika.altoEditor.presentation.facade;

import org.hibernate.search.engine.search.query.SearchResult;
import org.springframework.stereotype.Component;

import cz.inovatika.altoEditor.config.properties.KrameriusProperties;
import cz.inovatika.altoEditor.domain.enums.BatchPriority;
import cz.inovatika.altoEditor.domain.model.AltoVersion;
import cz.inovatika.altoEditor.domain.service.AltoVersionService;
import cz.inovatika.altoEditor.domain.service.container.AltoVersionUploadContent;
import cz.inovatika.altoEditor.domain.service.container.AltoVersionWithContent;
import cz.inovatika.altoEditor.infrastructure.kramerius.KrameriusService;
import cz.inovatika.altoEditor.presentation.dto.request.AltoVersionSearchRelatedRequest;
import cz.inovatika.altoEditor.presentation.dto.request.AltoVersionSearchRequest;
import cz.inovatika.altoEditor.presentation.dto.response.AltoVersionDto;
import cz.inovatika.altoEditor.presentation.dto.response.AltoVersionSearchDto;
import cz.inovatika.altoEditor.presentation.dto.response.BatchDto;
import cz.inovatika.altoEditor.presentation.dto.response.SearchResultsDto;
import cz.inovatika.altoEditor.presentation.mapper.AltoVersionMapper;
import cz.inovatika.altoEditor.presentation.mapper.BatchMapper;
import cz.inovatika.altoEditor.presentation.security.UserContextService;
import lombok.RequiredArgsConstructor;

/**
 * Facade for ALTO version operations: search, get content (related/active/specific version),
 * OCR, images, create/update version, state transitions, and per-page ALTO generation.
 */
@Component
@RequiredArgsConstructor
public class AltoVersionFacade {

    private final AltoVersionService service;

    private final KrameriusService krameriusService;

    private final KrameriusProperties krameriusConfig;

    private final UserContextService userContext;

    private final AltoVersionMapper mapper;

    private final BatchMapper batchMapper;

    /** Search ALTO versions related to current user (or ACTIVE). */
    public SearchResultsDto<AltoVersionSearchDto> searchRelated(AltoVersionSearchRelatedRequest request) {
        SearchResult<AltoVersion> results = service.searchRelated(
                userContext.getUserId(),
                request.getInstance(),
                request.getTargetPid(),
                request.getHierarchyPid(),
                request.getTitle(),
                request.getCreatedAfter(),
                request.getCreatedBefore(),
                request.getStates(),
                request.getOffset(),
                request.getLimit());
        
        return SearchResultsDto.<AltoVersionSearchDto>builder()
                .items(results.hits().stream().map(mapper::toSearchDto).toList())
                .total(results.total().hitCount())
                .build();
    }

    /** Search all ALTO versions (curator; optional filters). */
    public SearchResultsDto<AltoVersionSearchDto> searchAll(AltoVersionSearchRequest request) {
        SearchResult<AltoVersion> results = service.search(
                request.getUsers(),
                request.getInstance(),
                request.getTargetPid(),
                request.getHierarchyPid(),
                request.getTitle(),
                request.getCreatedAfter(),
                request.getCreatedBefore(),
                request.getStates(),
                request.getOffset(),
                request.getLimit());
        
        return SearchResultsDto.<AltoVersionSearchDto>builder()
                .items(results.hits().stream().map(mapper::toSearchDto).toList())
                .total(results.total().hitCount())
                .build();
    }

    /** Get ALTO for current user; if none, create from Kramerius and return. */
    public AltoVersionDto getRelatedAlto(String pid, String instanceId) {
        AltoVersionWithContent digitalObjectWithContent = service.findRelatedAlto(pid,
                userContext.getUserId());

        if (digitalObjectWithContent == null) {
            digitalObjectWithContent = service.createInitialVersion(pid, instanceId);
        }

        return mapper.toDto(digitalObjectWithContent);
    }

    /** Get currently active ALTO for the given page PID. */
    public AltoVersionDto getActiveAlto(String pid) {
        AltoVersionWithContent digitalObjectWithContent = service.getActiveAlto(pid);

        return mapper.toDto(digitalObjectWithContent);
    }

    /** Get specific ALTO revision by PID and version number. */
    public AltoVersionDto getAltoVersion(String pid, Integer version) {
        AltoVersionWithContent digitalObjectWithContent = service.getAltoVersion(pid, version);

        return mapper.toDto(digitalObjectWithContent);
    }

    /** Create new version or replace pending version for current user. */
    public AltoVersionDto updateOrCreateVersion(String pid, byte[] altoContent) {
        AltoVersionWithContent digitalObjectWithContent = service.updateOrCreateVersion(
            pid, userContext.getUserId(), altoContent);

        return mapper.toDto(digitalObjectWithContent);
    }

    /** Extract OCR text from ALTO version (by DB id). */
    public String getOcr(Integer objectId) {
        return service.getOcr(objectId);
    }

    public byte[] getImage(String pid, String instanceId) {
        return service.getKrameriusObjectImage(
                pid,
                instanceId != null ? instanceId : krameriusConfig.getDefaultInstanceId());
    }

    /** Start per-page ALTO generation batch (selected engine). */
    public BatchDto generateAlto(String pid, String engine, BatchPriority priority) {
        return batchMapper.toDto(service.generateAlto(pid, engine, priority, userContext.getUserId()));
    }

    /**
     * Set the specified ALTO version as the ACTIVE version for its PID and instance.
     * This operation:
     * - Uploads the related ALTO and OCR content to all Kramerius instances for live use.
     * - Changes the object's state to 'ACTIVE' (making it the default for editing and viewing).
     * - Archives previous ACTIVE version for the same PID, only if the target ALTO version is not already ACTIVE.
     * - Archives all STALE versions of the same PID.
     * Target ALTO version can be in any state.
     * When used on already ACTIVE object, it refreshes content in all Kramerius instances.
     *
     * Permitted state transitions are any state -> ACTIVE.
     */
    public void accept(int versionId) {
        AltoVersionUploadContent content = service.getAltoVersionUploadContent(versionId);

        krameriusService.uploadAltoOcr(content.getPid(), content.getAltoContent(), content.getOcrContent());

        service.accept(versionId);
    }

    /**
     * Reject an ALTO version by its ID.
     * Only permitted state transition is PENDING -> REJECTED
     */
    public void reject(int versionId) {
        service.reject(versionId);
    }

    /**
     * Archive an ALTO version by its ID.
     * Only permitted state transition is PENDING -> ARCHIVED
     */
    public void archive(int versionId) {
        service.archive(versionId);
    }
}
