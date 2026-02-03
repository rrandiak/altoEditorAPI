package cz.inovatika.altoEditor.infrastructure.process.retrieve;

import org.springframework.stereotype.Component;

import cz.inovatika.altoEditor.domain.model.Batch;
import cz.inovatika.altoEditor.domain.service.AltoVersionService;
import cz.inovatika.altoEditor.domain.service.BatchService;
import cz.inovatika.altoEditor.domain.service.ObjectHierarchyService;
import cz.inovatika.altoEditor.domain.service.UserService;
import cz.inovatika.altoEditor.infrastructure.kramerius.KrameriusService;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class RetrieveHierarchyProcessFactory {

    private final BatchService batchService;

    private final KrameriusService krameriusService;

    private final AltoVersionService altoVersionService;

    private final ObjectHierarchyService objectHierarchyService;

    private final UserService userService;

    public RetrieveHierarchyProcess create(Batch batch) {
        return new RetrieveHierarchyProcess(
                batchService,
                krameriusService,
                altoVersionService,
                objectHierarchyService,
                userService.getUserByUsername(batch.getInstance()).getId(),
                batch);
    }
}
