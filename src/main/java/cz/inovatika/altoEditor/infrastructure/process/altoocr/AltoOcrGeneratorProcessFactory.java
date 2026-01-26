package cz.inovatika.altoEditor.infrastructure.process.altoocr;

import org.springframework.stereotype.Component;

import cz.inovatika.altoEditor.config.properties.ProcessorsProperties;
import cz.inovatika.altoEditor.domain.model.Batch;
import cz.inovatika.altoEditor.domain.repository.DigitalObjectRepository;
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
    private final DigitalObjectRepository digitalObjectRepository;
    private final AkubraService akubraService;
    private final KrameriusService krameriusService;
    private final ProcessorsProperties processorsProperties;

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
                processorsProperties.getPeroProperties());
    }
}