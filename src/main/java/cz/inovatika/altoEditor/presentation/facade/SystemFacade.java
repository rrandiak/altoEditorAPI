package cz.inovatika.altoEditor.presentation.facade;

import java.util.List;

import org.springframework.stereotype.Component;

import cz.inovatika.altoEditor.config.properties.KrameriusProperties;
import cz.inovatika.altoEditor.domain.enums.BatchPriority;
import cz.inovatika.altoEditor.domain.model.Batch;
import cz.inovatika.altoEditor.domain.service.BatchService;
import cz.inovatika.altoEditor.presentation.dto.response.BatchDto;
import cz.inovatika.altoEditor.presentation.dto.response.InfoDto;
import cz.inovatika.altoEditor.presentation.dto.response.KrameriusInstance;
import cz.inovatika.altoEditor.presentation.mapper.BatchMapper;
import cz.inovatika.altoEditor.presentation.mapper.KrameriusIntanceMapper;
import cz.inovatika.altoEditor.presentation.security.UserContextService;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class SystemFacade {
    
    private final BatchService batchService;

    private final UserContextService userContext;

    private final BatchMapper mapper;

    private final KrameriusProperties krameriusProperties;

    private final KrameriusIntanceMapper krameriusIntanceMapper;

    public InfoDto getInfo() {

        List<KrameriusInstance> instances = krameriusProperties.getKrameriusInstances().entrySet().stream()
                .map(entry -> krameriusIntanceMapper.toDto(entry.getKey(), entry.getValue()))
                .toList();

        InfoDto info = new InfoDto();
        info.setInstances(instances);

        return info;
    }

    /** Create reindex batch. Picked up by scheduler. */
    public BatchDto createReindexBatch(BatchPriority priority) {
        if (userContext.getUserId() == null) {
            throw new IllegalStateException("No authenticated user");
        }
        Batch batch = batchService.createReindexBatch(priority, userContext.getUserId());
        return mapper.toDto(batch);
    }
}
