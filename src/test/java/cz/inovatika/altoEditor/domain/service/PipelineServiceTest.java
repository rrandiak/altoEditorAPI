package cz.inovatika.altoEditor.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;

import cz.inovatika.altoEditor.domain.enums.AltoVersionState;
import cz.inovatika.altoEditor.domain.enums.BatchPriority;
import cz.inovatika.altoEditor.domain.enums.BatchType;
import cz.inovatika.altoEditor.domain.enums.HierarchyGenerateScope;
import cz.inovatika.altoEditor.domain.enums.PipelineStage;
import cz.inovatika.altoEditor.domain.model.Batch;
import cz.inovatika.altoEditor.domain.model.User;
import cz.inovatika.altoEditor.domain.model.dto.AltoVersionSearchFilter;
import cz.inovatika.altoEditor.domain.repository.BatchRepository;

@ExtendWith(MockitoExtension.class)
class PipelineServiceTest {

    @Mock
    private BatchRepository batchRepository;
    @Mock
    private UserService userService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private PipelineService service;

    private static final Long USER_ID = 7L;
    private static final Long ENGINE_USER_ID = 42L;
    private static final String PID = "uuid:root";
    private static final String INSTANCE = "k7-mzk";
    private static final String ENGINE = "pero-vut";

    @BeforeEach
    void setUp() {
        service = new PipelineService(batchRepository, userService, objectMapper);

        // save() assigns a sequential id (so parent.getId() is available for children) and returns the arg.
        lenient().when(batchRepository.save(any(Batch.class))).thenAnswer(inv -> {
            Batch b = inv.getArgument(0);
            if (b.getId() == null) {
                b.setId(nextId++);
            }
            return b;
        });
        lenient().when(userService.getUserById(USER_ID)).thenReturn(mock(User.class));
    }

    private int nextId = 1;

    private List<Batch> capturedSaves() {
        ArgumentCaptor<Batch> captor = ArgumentCaptor.forClass(Batch.class);
        org.mockito.Mockito.verify(batchRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        return captor.getAllValues();
    }

    @Test
    @DisplayName("all three stages: parent + 3 children with canonical order and correct inputs")
    void createsParentAndThreeChildren() throws Exception {
        User engineUser = mock(User.class);
        when(engineUser.getId()).thenReturn(ENGINE_USER_ID);
        when(userService.getUserByUsername(ENGINE)).thenReturn(engineUser);

        Batch parent = service.createPipeline(PID, INSTANCE, ENGINE, HierarchyGenerateScope.NO_PENDING_NOR_ACTIVE,
                List.of(PipelineStage.ACCEPT, PipelineStage.RETRIEVE, PipelineStage.GENERATE), BatchPriority.HIGH, USER_ID);

        List<Batch> saved = capturedSaves();
        assertThat(saved).hasSize(4);

        Batch savedParent = saved.get(0);
        assertThat(savedParent.getType()).isEqualTo(BatchType.PIPELINE);
        assertThat(savedParent).isSameAs(parent);
        assertThat(savedParent.getParentBatchId()).isNull();

        List<Batch> children = saved.subList(1, 4);
        // canonical order enforced regardless of request order
        assertThat(children).extracting(Batch::getType).containsExactly(
                BatchType.RETRIEVE_HIERARCHY, BatchType.GENERATE_FOR_HIERARCHY, BatchType.ACCEPT_VERSIONS);
        assertThat(children).extracting(Batch::getStageOrder).containsExactly(0, 1, 2);
        assertThat(children).allMatch(c -> c.getParentBatchId().equals(parent.getId()));
        assertThat(children).allMatch(c -> c.getPriority() == BatchPriority.HIGH);

        // generate carries the scope
        assertThat(children.get(1).getData()).contains("NO_PENDING_NOR_ACTIVE");

        // accept filter is derived: hierarchyPid=pid, PENDING, engine user (no instance —
        // instance is not a mapped search field, see PipelineService)
        AltoVersionSearchFilter filter = objectMapper.readValue(children.get(2).getData(), AltoVersionSearchFilter.class);
        assertThat(filter.getHierarchyPid()).isEqualTo(PID);
        assertThat(filter.getInstance()).isNull();
        assertThat(filter.getStates()).containsExactly(AltoVersionState.PENDING);
        assertThat(filter.getUsers()).containsExactly(ENGINE_USER_ID);
    }

    @Test
    @DisplayName("subset [GENERATE, ACCEPT]: stageOrder compacted to 0,1")
    void compactsStageOrderForSubset() {
        User engineUser = mock(User.class);
        when(engineUser.getId()).thenReturn(ENGINE_USER_ID);
        when(userService.getUserByUsername(ENGINE)).thenReturn(engineUser);

        service.createPipeline(PID, INSTANCE, ENGINE, HierarchyGenerateScope.ALL,
                List.of(PipelineStage.GENERATE, PipelineStage.ACCEPT), null, USER_ID);

        List<Batch> children = capturedSaves().subList(1, 3);
        assertThat(children).extracting(Batch::getType).containsExactly(
                BatchType.GENERATE_FOR_HIERARCHY, BatchType.ACCEPT_VERSIONS);
        assertThat(children).extracting(Batch::getStageOrder).containsExactly(0, 1);
    }

    @Test
    @DisplayName("validation: empty stages, missing engine, missing instance are rejected")
    void validation() {
        assertThatThrownBy(() -> service.createPipeline(PID, INSTANCE, ENGINE, null, List.of(), null, USER_ID))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> service.createPipeline(PID, INSTANCE, null, null,
                List.of(PipelineStage.GENERATE), null, USER_ID))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> service.createPipeline(PID, null, ENGINE, null,
                List.of(PipelineStage.RETRIEVE), null, USER_ID))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
