package cz.inovatika.altoEditor.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import cz.inovatika.altoEditor.domain.enums.BatchPriority;
import cz.inovatika.altoEditor.domain.enums.BatchType;
import cz.inovatika.altoEditor.domain.model.Batch;
import cz.inovatika.altoEditor.domain.model.DigitalObject;
import cz.inovatika.altoEditor.domain.model.User;
import cz.inovatika.altoEditor.domain.repository.BatchRepository;
import cz.inovatika.altoEditor.domain.repository.DigitalObjectRepository;
import cz.inovatika.altoEditor.exception.DigitalObjectNotFoundException;
import cz.inovatika.altoEditor.infrastructure.kramerius.KrameriusService;
import cz.inovatika.altoEditor.infrastructure.kramerius.model.KrameriusObjectMetadata;
import jakarta.persistence.EntityManager;

/**
 * Unit tests for {@link ObjectHierarchyService}.
 * Search method uses Hibernate Search and is better tested via integration tests.
 */
class ObjectHierarchyServiceTest {

    @Mock
    private DigitalObjectRepository digitalObjectRepository;
    @Mock
    private KrameriusService krameriusService;
    @Mock
    private EntityManager entityManager;
    @Mock
    private UserService userService;
    @Mock
    private BatchRepository batchRepository;

    @InjectMocks
    private ObjectHierarchyService service;

    private static final String PAGE_PID = "uuid:11111111-1111-2222-3333-444444444444";
    private static final String ROOT_PID = "uuid:22222222-1111-2222-3333-444444444444";
    private static final String INSTANCE = "dk";
    private static final Long USER_ID = 10L;
    private static final User USER = User.builder().id(USER_ID).username("user").build();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Nested
    @DisplayName("fetchAndStore")
    class FetchAndStore {

        @Test
        @DisplayName("returns existing DigitalObject when already in repository")
        void returnsExisting_whenAlreadyStored() {
            DigitalObject existing = DigitalObject.builder().pid(PAGE_PID).build();
            when(digitalObjectRepository.findById(any(UUID.class))).thenReturn(Optional.of(existing));

            DigitalObject result = service.fetchAndStore(PAGE_PID, INSTANCE);

            assertThat(result).isEqualTo(existing);
            verify(digitalObjectRepository).findById(any(UUID.class));
            verify(krameriusService, never()).getObjectMetadata(any(), any());
        }

        @Test
        @DisplayName("throws DigitalObjectNotFoundException when Kramerius returns null metadata")
        void throws_whenMetadataNull() {
            when(digitalObjectRepository.findById(any(UUID.class))).thenReturn(Optional.empty());
            when(krameriusService.getObjectMetadata(PAGE_PID, INSTANCE)).thenReturn(null);

            assertThrows(DigitalObjectNotFoundException.class, () -> service.fetchAndStore(PAGE_PID, INSTANCE));
        }

        @Test
        @DisplayName("creates and saves single node when metadata has no parent")
        void createsSingleNode_whenNoParent() {
            KrameriusObjectMetadata metadata = KrameriusObjectMetadata.builder()
                    .pid(PAGE_PID)
                    .model("page")
                    .title("Page 1")
                    .level(1)
                    .indexInParent(0)
                    .parentPid(null)
                    .rootPid(PAGE_PID)
                    .build();
            DigitalObject saved = DigitalObject.builder().pid(PAGE_PID).title("Page 1").build();

            when(digitalObjectRepository.findById(any(UUID.class))).thenReturn(Optional.empty());
            when(krameriusService.getObjectMetadata(PAGE_PID, INSTANCE)).thenReturn(metadata);
            when(digitalObjectRepository.save(any(DigitalObject.class))).thenReturn(saved);

            DigitalObject result = service.fetchAndStore(PAGE_PID, INSTANCE);

            assertThat(result).isEqualTo(saved);
            verify(digitalObjectRepository).save(any(DigitalObject.class));
        }

        @Test
        @DisplayName("creates hierarchy from root to leaf when parent not in DB")
        void createsHierarchy_whenParentNotInDb() {
            KrameriusObjectMetadata leafMeta = KrameriusObjectMetadata.builder()
                    .pid(PAGE_PID)
                    .model("page")
                    .title("Page")
                    .level(2)
                    .indexInParent(0)
                    .parentPid(ROOT_PID)
                    .rootPid(ROOT_PID)
                    .build();
            KrameriusObjectMetadata rootMeta = KrameriusObjectMetadata.builder()
                    .pid(ROOT_PID)
                    .model("monograph")
                    .title("Monograph")
                    .level(0)
                    .indexInParent(null)
                    .parentPid(null)
                    .rootPid(ROOT_PID)
                    .build();

            DigitalObject rootObj = DigitalObject.builder().pid(ROOT_PID).build();
            DigitalObject leafObj = DigitalObject.builder().pid(PAGE_PID).parent(rootObj).build();

            when(digitalObjectRepository.findById(any(UUID.class))).thenReturn(Optional.empty());
            when(krameriusService.getObjectMetadata(PAGE_PID, INSTANCE)).thenReturn(leafMeta);
            when(krameriusService.getObjectMetadata(ROOT_PID, INSTANCE)).thenReturn(rootMeta);
            when(digitalObjectRepository.save(any(DigitalObject.class))).thenReturn(rootObj, leafObj);

            DigitalObject result = service.fetchAndStore(PAGE_PID, INSTANCE);

            assertThat(result).isEqualTo(leafObj);
            verify(digitalObjectRepository, times(2)).save(any(DigitalObject.class));
        }

        @Test
        @DisplayName("does not call Kramerius when object already stored")
        void doesNotCallKramerius_whenExisting() {
            DigitalObject existing = DigitalObject.builder().pid(PAGE_PID).build();
            when(digitalObjectRepository.findById(any(UUID.class))).thenReturn(Optional.of(existing));

            service.fetchAndStore(PAGE_PID, INSTANCE);

            verify(krameriusService, never()).getObjectMetadata(any(), any());
        }
    }

    @Nested
    @DisplayName("store")
    class Store {

        @Test
        @DisplayName("returns existing DigitalObject when uuid already in repository")
        void returnsExisting_whenAlreadyStored() {
            KrameriusObjectMetadata metadata = KrameriusObjectMetadata.builder()
                    .pid(PAGE_PID)
                    .model("page")
                    .title("Page")
                    .level(1)
                    .indexInParent(0)
                    .parentPid(null)
                    .rootPid(PAGE_PID)
                    .build();
            DigitalObject existing = DigitalObject.builder().pid(PAGE_PID).build();
            when(digitalObjectRepository.findById(metadata.getUuid())).thenReturn(Optional.of(existing));

            DigitalObject result = service.store(metadata);

            assertThat(result).isEqualTo(existing);
            verify(digitalObjectRepository).findById(metadata.getUuid());
        }

        @Test
        @DisplayName("saves new DigitalObject when not present, without parent")
        void savesNew_whenNotPresent_noParent() {
            KrameriusObjectMetadata metadata = KrameriusObjectMetadata.builder()
                    .pid(PAGE_PID)
                    .model("page")
                    .title("Page 1")
                    .level(1)
                    .indexInParent(0)
                    .parentPid(null)
                    .rootPid(PAGE_PID)
                    .build();
            DigitalObject saved = DigitalObject.builder().pid(PAGE_PID).build();

            when(digitalObjectRepository.findById(metadata.getUuid())).thenReturn(Optional.empty());
            when(digitalObjectRepository.save(any(DigitalObject.class))).thenReturn(saved);

            DigitalObject result = service.store(metadata);

            assertThat(result).isEqualTo(saved);
            verify(digitalObjectRepository).save(any(DigitalObject.class));
        }

        @Test
        @DisplayName("saves new DigitalObject with parent when parent exists")
        void savesNew_withParent_whenParentExists() {
            DigitalObject parent = DigitalObject.builder().pid(ROOT_PID).build();
            KrameriusObjectMetadata metadata = KrameriusObjectMetadata.builder()
                    .pid(PAGE_PID)
                    .model("page")
                    .title("Page")
                    .level(1)
                    .indexInParent(0)
                    .parentPid(ROOT_PID)
                    .rootPid(ROOT_PID)
                    .build();
            DigitalObject saved = DigitalObject.builder().pid(PAGE_PID).parent(parent).build();

            when(digitalObjectRepository.findById(metadata.getUuid())).thenReturn(Optional.empty());
            when(digitalObjectRepository.findById(metadata.getParentUuid())).thenReturn(Optional.of(parent));
            when(digitalObjectRepository.save(any(DigitalObject.class))).thenReturn(saved);

            DigitalObject result = service.store(metadata);

            assertThat(result).isEqualTo(saved);
            verify(digitalObjectRepository).save(any(DigitalObject.class));
        }
    }

    @Nested
    @DisplayName("generateAlto")
    class GenerateAlto {

        @Test
        @DisplayName("saves batch and submits process, returns batch")
        void savesBatchAndSubmitsProcess() {
            Batch batch = Batch.builder()
                    .id(1)
                    .type(BatchType.GENERATE_FOR_HIERARCHY)
                    .pid(PAGE_PID)
                    .priority(BatchPriority.MEDIUM)
                    .createdBy(USER)
                    .build();
            when(userService.getUserById(USER_ID)).thenReturn(USER);
            when(batchRepository.save(any(Batch.class))).thenReturn(batch);

            Batch result = service.createGenerateAltoBatch(PAGE_PID, BatchPriority.MEDIUM, USER_ID);

            assertThat(result).isEqualTo(batch);
            assertThat(result.getType()).isEqualTo(BatchType.GENERATE_FOR_HIERARCHY);
            assertThat(result.getPid()).isEqualTo(PAGE_PID);
            verify(batchRepository).save(any(Batch.class));
        }
    }

    @Nested
    @DisplayName("fetchFromKramerius")
    class FetchFromKramerius {

        @Test
        @DisplayName("saves RETRIEVE_HIERARCHY batch and submits process, returns batch")
        void savesBatchAndSubmitsProcess() {
            Batch batch = Batch.builder()
                    .id(1)
                    .type(BatchType.RETRIEVE_HIERARCHY)
                    .pid(PAGE_PID)
                    .priority(BatchPriority.HIGH)
                    .createdBy(USER)
                    .build();
            when(userService.getUserById(USER_ID)).thenReturn(USER);
            when(batchRepository.save(any(Batch.class))).thenReturn(batch);

            Batch result = service.createFetchFromKrameriusBatch(PAGE_PID, BatchPriority.HIGH, USER_ID);

            assertThat(result).isEqualTo(batch);
            assertThat(result.getType()).isEqualTo(BatchType.RETRIEVE_HIERARCHY);
            assertThat(result.getPid()).isEqualTo(PAGE_PID);
            verify(batchRepository).save(any(Batch.class));
        }
    }
}
