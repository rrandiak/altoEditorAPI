package cz.inovatika.altoEditor.infrastructure.process.accept;

import org.springframework.stereotype.Component;

import cz.inovatika.altoEditor.domain.model.Batch;
import cz.inovatika.altoEditor.domain.repository.AltoVersionRepository;
import cz.inovatika.altoEditor.domain.service.AltoVersionService;
import cz.inovatika.altoEditor.domain.service.BatchService;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class AcceptEngineVersionsProcessFactory {

    private final BatchService batchService;
    private final AltoVersionService altoVersionService;
    private final AltoVersionRepository altoVersionRepository;

    public AcceptEngineVersionsProcess create(Batch batch) {
        return new AcceptEngineVersionsProcess(batchService, altoVersionService, altoVersionRepository, batch);
    }
}
