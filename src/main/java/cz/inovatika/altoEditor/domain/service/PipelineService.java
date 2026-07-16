package cz.inovatika.altoEditor.domain.service;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import cz.inovatika.altoEditor.domain.enums.AltoVersionState;
import cz.inovatika.altoEditor.domain.enums.BatchPriority;
import cz.inovatika.altoEditor.domain.enums.BatchType;
import cz.inovatika.altoEditor.domain.enums.HierarchyGenerateScope;
import cz.inovatika.altoEditor.domain.enums.PipelineStage;
import cz.inovatika.altoEditor.domain.model.Batch;
import cz.inovatika.altoEditor.domain.model.User;
import cz.inovatika.altoEditor.domain.model.dto.AltoVersionSearchFilter;
import cz.inovatika.altoEditor.domain.model.dto.HierarchyGenerateInput;
import cz.inovatika.altoEditor.domain.repository.BatchRepository;
import lombok.RequiredArgsConstructor;

/**
 * Creates a unified pipeline: a parent {@link BatchType#PIPELINE} batch plus one child stage
 * batch per selected {@link PipelineStage}, in canonical order. Because the pipeline params
 * fully determine every stage's input, all children are created up-front; the scheduler runs
 * them in order (see the dependency-aware claim) and the coordinator derives parent state.
 */
@Service
@RequiredArgsConstructor
public class PipelineService {

    private final BatchRepository batchRepository;
    private final UserService userService;
    private final ObjectMapper objectMapper;

    @Transactional
    public Batch createPipeline(String pid, String instance, String engine, HierarchyGenerateScope scope,
            List<PipelineStage> stages, BatchPriority priority, Long userId) {

        if (stages == null || stages.isEmpty()) {
            throw new IllegalArgumentException("Pipeline must include at least one stage");
        }
        if (pid == null || pid.isBlank()) {
            throw new IllegalArgumentException("Pipeline requires a pid");
        }

        List<PipelineStage> ordered = stages.stream()
                .distinct()
                .sorted(Comparator.comparingInt(Enum::ordinal))
                .toList();

        boolean hasRetrieve = ordered.contains(PipelineStage.RETRIEVE);
        boolean hasGenerate = ordered.contains(PipelineStage.GENERATE);
        boolean hasAccept = ordered.contains(PipelineStage.ACCEPT);

        if ((hasRetrieve || hasGenerate) && (instance == null || instance.isBlank())) {
            throw new IllegalArgumentException("Pipeline requires an instance for the retrieve/generate stages");
        }
        // Generate needs an engine to run; accept needs it to know whose PENDING versions to accept.
        if ((hasGenerate || hasAccept) && (engine == null || engine.isBlank())) {
            throw new IllegalArgumentException("Pipeline requires an engine for the generate/accept stages");
        }

        User user = userService.getUserById(userId);
        BatchPriority effectivePriority = priority != null ? priority : BatchPriority.MEDIUM;

        Batch parent = batchRepository.save(Batch.builder()
                .type(BatchType.PIPELINE)
                .pid(pid)
                .instance(instance)
                .engine(engine)
                .priority(effectivePriority)
                .createdBy(user)
                .build());

        int stageOrder = 0;
        for (PipelineStage stage : ordered) {
            batchRepository.save(
                    buildChild(stage, parent.getId(), pid, instance, engine, scope, effectivePriority, user, stageOrder));
            stageOrder++;
        }

        return parent;
    }

    private Batch buildChild(PipelineStage stage, Integer parentId, String pid, String instance, String engine,
            HierarchyGenerateScope scope, BatchPriority priority, User user, int stageOrder) {

        Batch.BatchBuilder builder = Batch.builder()
                .type(stage.getBatchType())
                .parentBatchId(parentId)
                .stageOrder(stageOrder)
                .pid(pid)
                .instance(instance)
                .priority(priority)
                .createdBy(user);

        switch (stage) {
            case RETRIEVE -> {
                // pid + instance already set
            }
            case GENERATE -> {
                HierarchyGenerateInput input = HierarchyGenerateInput.builder()
                        .scope(scope != null ? scope : HierarchyGenerateScope.ALL)
                        .build();
                builder.engine(engine).data(writeJson(input, "hierarchy generate input"));
            }
            case ACCEPT -> {
                // NB: no instance filter — `instance` is not a mapped field on the AltoVersion
                // search index (HSEARCH000610), and accept uploads to all configured instances
                // anyway. Match the manual-accept filter: hierarchy + engine user + PENDING.
                Long engineUserId = userService.getUserByUsername(engine).getId();
                AltoVersionSearchFilter filter = AltoVersionSearchFilter.builder()
                        .hierarchyPid(pid)
                        .users(List.of(engineUserId))
                        .states(List.of(AltoVersionState.PENDING))
                        .build();
                builder.engine(engine).data(writeJson(filter, "accept versions filter"));
            }
        }

        return builder.build();
    }

    private String writeJson(Object value, String what) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize " + what, e);
        }
    }
}
