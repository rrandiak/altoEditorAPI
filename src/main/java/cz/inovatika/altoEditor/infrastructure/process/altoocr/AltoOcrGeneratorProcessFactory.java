package cz.inovatika.altoEditor.infrastructure.process.altoocr;

import org.springframework.stereotype.Component;

import cz.inovatika.altoEditor.config.properties.EnginesProperties;
import cz.inovatika.altoEditor.domain.model.Batch;
import cz.inovatika.altoEditor.domain.repository.AltoVersionRepository;
import cz.inovatika.altoEditor.domain.service.BatchService;
import cz.inovatika.altoEditor.infrastructure.kramerius.KrameriusService;
import cz.inovatika.altoEditor.infrastructure.storage.AkubraService;
import cz.inovatika.altoEditor.infrastructure.storage.WorkDirectoryService;
import cz.inovatika.altoEditor.presentation.security.UserProfile;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class AltoOcrGeneratorProcessFactory {

    private final WorkDirectoryService workDirectoryService;
    private final BatchService batchService;
    private final AltoVersionRepository digitalObjectRepository;
    private final AkubraService akubraService;
    private final KrameriusService krameriusService;
    private final EnginesProperties enginesProperties;

    public AltoOcrGeneratorProcess create(
            Batch batch,
            UserProfile userProfile) {
        return new AltoOcrGeneratorProcess(
                workDirectoryService,
                batchService,
                digitalObjectRepository,
                akubraService,
                krameriusService,
                batch,
                userProfile,
                enginesProperties.getEngines().get(batch.getEngine()));
    }
}