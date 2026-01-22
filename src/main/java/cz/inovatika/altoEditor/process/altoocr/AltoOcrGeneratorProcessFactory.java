package cz.inovatika.altoEditor.process.altoocr;

import org.springframework.stereotype.Component;

import cz.inovatika.altoEditor.api.auth.UserProfile;
import cz.inovatika.altoEditor.config.ProcessorsConfig;
import cz.inovatika.altoEditor.core.entity.Batch;
import cz.inovatika.altoEditor.core.repository.DigitalObjectRepository;
import cz.inovatika.altoEditor.core.service.BatchService;
import cz.inovatika.altoEditor.kramerius.KrameriusService;
import cz.inovatika.altoEditor.storage.AkubraService;
import cz.inovatika.altoEditor.storage.WorkDirectoryService;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class AltoOcrGeneratorProcessFactory {

    private final WorkDirectoryService workDirectoryService;
    private final BatchService batchService;
    private final DigitalObjectRepository digitalObjectRepository;
    private final AkubraService akubraService;
    private final KrameriusService krameriusService;

    public AltoOcrGeneratorProcess create(
            Batch batch,
            UserProfile userProfile,
            ProcessorsConfig.ProcessorConfig generatorConfig) {
        return new AltoOcrGeneratorProcess(
                workDirectoryService,
                batchService,
                digitalObjectRepository,
                akubraService,
                krameriusService,
                batch,
                userProfile,
                generatorConfig);
    }
}