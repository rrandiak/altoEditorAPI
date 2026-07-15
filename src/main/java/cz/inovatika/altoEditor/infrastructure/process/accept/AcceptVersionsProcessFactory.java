package cz.inovatika.altoEditor.infrastructure.process.accept;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import cz.inovatika.altoEditor.config.properties.BatchProperties;
import cz.inovatika.altoEditor.domain.model.Batch;
import cz.inovatika.altoEditor.domain.service.AltoVersionService;
import cz.inovatika.altoEditor.domain.service.BatchService;
import cz.inovatika.altoEditor.domain.service.ObjectHierarchyService;
import cz.inovatika.altoEditor.infrastructure.kramerius.KrameriusService;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class AcceptVersionsProcessFactory {

    private final BatchService batchService;
    private final AltoVersionService altoVersionService;
    private final KrameriusService krameriusService;
    private final ObjectHierarchyService objectHierarchyService;
    private final BatchProperties batchProperties;
    private final ObjectMapper objectMapper;

    public AcceptVersionsProcess create(Batch batch) {
        return new AcceptVersionsProcess(
                batchService,
                altoVersionService,
                krameriusService,
                objectHierarchyService,
                batchProperties,
                objectMapper,
                batch);
    }
}
