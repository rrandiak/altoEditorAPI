package cz.inovatika.altoEditor.presentation.facade;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import cz.inovatika.altoEditor.config.properties.EnginesProperties;
import cz.inovatika.altoEditor.config.properties.KrameriusProperties;
import cz.inovatika.altoEditor.domain.model.Batch;
import cz.inovatika.altoEditor.domain.service.BatchService;
import cz.inovatika.altoEditor.domain.service.PipelineService;
import cz.inovatika.altoEditor.presentation.dto.request.PipelineRequest;
import cz.inovatika.altoEditor.presentation.dto.response.BatchDto;
import cz.inovatika.altoEditor.presentation.mapper.BatchMapper;
import cz.inovatika.altoEditor.presentation.security.UserContextService;
import lombok.RequiredArgsConstructor;

/**
 * Facade for the unified pipeline: create a pipeline (parent + child stage batches) and read
 * a pipeline with its stages. Batches are only created (PLANNED); the scheduler runs them.
 */
@Component
@RequiredArgsConstructor
public class PipelineFacade {

    private final PipelineService pipelineService;
    private final BatchService batchService;
    private final KrameriusProperties krameriusConfig;
    private final EnginesProperties enginesProperties;
    private final UserContextService userContext;
    private final BatchMapper batchMapper;

    /** Create a pipeline; returns the parent batch DTO. */
    public BatchDto create(PipelineRequest request) {
        String instance = request.getInstance() != null ? request.getInstance()
                : krameriusConfig.getDefaultInstance();
        if (request.getEngine() != null) {
            enginesProperties.getEngineConfig(request.getEngine()); // validate engine exists
        }

        Batch parent = pipelineService.createPipeline(
                request.getPid(),
                instance,
                request.getEngine(),
                request.getScope(),
                request.getStages(),
                request.getPriority(),
                userContext.getUserId());

        return batchMapper.toDto(parent);
    }

    /** The pipeline parent plus its child stage batches, ordered by stage. */
    public List<BatchDto> getStages(Integer pipelineId) {
        Batch parent = batchService.getById(pipelineId);
        List<BatchDto> result = new ArrayList<>();
        result.add(batchMapper.toDto(parent));
        batchService.findChildStages(pipelineId).forEach(child -> result.add(batchMapper.toDto(child)));
        return result;
    }
}
