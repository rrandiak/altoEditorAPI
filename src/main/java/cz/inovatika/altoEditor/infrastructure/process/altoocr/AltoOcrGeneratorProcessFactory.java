package cz.inovatika.altoEditor.infrastructure.process.altoocr;

import org.springframework.stereotype.Component;

import cz.inovatika.altoEditor.config.properties.EnginesProperties;
import cz.inovatika.altoEditor.domain.model.Batch;
import cz.inovatika.altoEditor.domain.service.AltoVersionService;
import cz.inovatika.altoEditor.domain.service.BatchService;
import cz.inovatika.altoEditor.domain.service.UserService;
import cz.inovatika.altoEditor.infrastructure.kramerius.KrameriusService;
import cz.inovatika.altoEditor.infrastructure.storage.WorkDirectoryService;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class AltoOcrGeneratorProcessFactory {

    private final WorkDirectoryService workDirectoryService;

    private final BatchService batchService;
    private final AltoVersionService altoVersionService;
    private final KrameriusService krameriusService;
    private final UserService userService;

    private final EnginesProperties enginesProperties;

    public AltoOcrGeneratorProcess create(Batch batch) {
        return new AltoOcrGeneratorProcess(
                workDirectoryService,
                batchService,
                altoVersionService,
                krameriusService,
                userService.getUserByUsername(batch.getEngine()).getId(),
                enginesProperties.getEngineConfig(batch.getEngine()),
                batch);
    }
}