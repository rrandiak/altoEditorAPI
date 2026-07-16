package cz.inovatika.altoEditor.infrastructure.process.altoocr;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import cz.inovatika.altoEditor.domain.model.Batch;
import cz.inovatika.altoEditor.domain.service.AltoVersionService;
import cz.inovatika.altoEditor.domain.service.BatchService;
import cz.inovatika.altoEditor.domain.service.UserService;
import cz.inovatika.altoEditor.infrastructure.kramerius.KrameriusService;
import cz.inovatika.altoEditor.infrastructure.process.altoocr.engine.OcrEngineFactory;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class AltoOcrGeneratorProcessFactory {

    private final BatchService batchService;
    private final AltoVersionService altoVersionService;
    private final KrameriusService krameriusService;
    private final UserService userService;

    private final OcrEngineFactory ocrEngineFactory;
    private final EngineExecutorServiceRegistry executorRegistry;
    private final ObjectMapper objectMapper;

    public AltoOcrGeneratorProcess create(Batch batch) {
        return new AltoOcrGeneratorProcess(
                batchService,
                altoVersionService,
                krameriusService,
                userService.getUserByUsername(batch.getEngine()).getId(),
                batch.getEngine(),
                ocrEngineFactory.getEngine(batch.getEngine()),
                executorRegistry,
                objectMapper,
                batch);
    }
}