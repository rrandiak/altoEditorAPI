package cz.inovatika.altoEditor.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import cz.inovatika.altoEditor.domain.enums.AltoVersionState;
import cz.inovatika.altoEditor.domain.enums.BatchPriority;
import cz.inovatika.altoEditor.domain.enums.BatchType;
import cz.inovatika.altoEditor.domain.enums.Datastream;
import cz.inovatika.altoEditor.domain.model.AltoVersion;
import cz.inovatika.altoEditor.domain.model.Batch;
import cz.inovatika.altoEditor.domain.model.DigitalObject;
import cz.inovatika.altoEditor.domain.model.User;
import cz.inovatika.altoEditor.domain.repository.AltoVersionRepository;
import cz.inovatika.altoEditor.domain.repository.BatchRepository;
import cz.inovatika.altoEditor.domain.repository.UserRepository;
import cz.inovatika.altoEditor.domain.service.container.AltoVersionUploadContent;
import cz.inovatika.altoEditor.domain.service.container.AltoVersionWithContent;
import cz.inovatika.altoEditor.exception.AltoNotFoundException;
import cz.inovatika.altoEditor.exception.AltoVersionAlreadyExistsException;
import cz.inovatika.altoEditor.exception.AltoVersionNotFoundException;
import cz.inovatika.altoEditor.infrastructure.editor.AltoXmlService;
import cz.inovatika.altoEditor.infrastructure.kramerius.KrameriusService;
import cz.inovatika.altoEditor.infrastructure.storage.AkubraService;
import jakarta.persistence.EntityManager;

/**
 * Unit tests for {@link AltoVersionService} with mocks.
 * Search methods (searchRelated, search, distinctPidsByAncestorPid) are tested in
 * {@link AltoVersionServiceSearchIT} with a real index.
 */
class AltoVersionServiceTest {

    @Mock
    private AltoVersionRepository repository;
    @Mock
    private ObjectHierarchyService objectHierarchyService;
    @Mock
    private UserService userService;
    @Mock
    private AkubraService akubraService;
    @Mock
    private KrameriusService krameriusService;
    @Mock
    private AltoXmlService altoXmlService;
    @Mock
    private EntityManager entityManager;
    @Mock
    private UserRepository userRepository;
    @Mock
    private BatchRepository batchRepository;

    @InjectMocks
    private AltoVersionService service;

    private static final String PID = "uuid:123e4567-e89b-12d3-a456-426614174000";
    private static final long USER_ID = 10L;
    private static final User USER = User.builder().id(USER_ID).username("editor").build();
    private static final int VERSION = 1;
    private static final byte[] ALTO_CONTENT = "alto-content".getBytes();
    private static final byte[] OCR_CONTENT = "ocr text".getBytes();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Nested
    @DisplayName("findRelated and findRelatedAlto")
    class FindRelated {

        @Test
        @DisplayName("findRelatedAlto returns content when related version exists")
        void findRelatedAlto_returnsContent_whenRelatedExists() {
            DigitalObject dobj = DigitalObject.builder().pid(PID).build();
            AltoVersion av = AltoVersion.builder()
                    .digitalObject(dobj)
                    .version(VERSION)
                    .user(USER)
                    .build();
            when(repository.findRelated(any(), eq(USER_ID))).thenReturn(Optional.of(av));
            when(akubraService.retrieveDsBinaryContent(eq(PID), eq(Datastream.ALTO), eq(VERSION)))
                    .thenReturn(ALTO_CONTENT);

            AltoVersionWithContent result = service.findRelatedAlto(PID, USER_ID);

            assertThat(result).isNotNull();
            assertThat(result.getAltoVersion()).isEqualTo(av);
            assertArrayEquals(ALTO_CONTENT, result.getContent());
        }

        @Test
        @DisplayName("findRelatedAlto returns null when no related version")
        void findRelatedAlto_returnsNull_whenNoRelated() {
            when(repository.findRelated(any(), eq(USER_ID))).thenReturn(Optional.empty());

            AltoVersionWithContent result = service.findRelatedAlto(PID, USER_ID);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("findRelated throws when PID does not start with uuid:")
        void findRelated_throws_whenInvalidPid() {
            assertThrows(IllegalArgumentException.class, () -> service.findRelated("invalid", USER_ID));
        }
    }

    @Nested
    @DisplayName("createInitialVersion")
    class CreateInitialVersion {

        @Test
        @DisplayName("creates initial version when no version exists and Kramerius returns ALTO")
        void createsInitialVersion_whenNoVersionAndAltoAvailable() {
            String instance = "dk";
            User instanceUser = User.builder().id(2L).username(instance).build();
            DigitalObject targetObj = DigitalObject.builder().pid(PID).build();
            AltoVersion saved = AltoVersion.builder()
                    .digitalObject(targetObj)
                    .version(0)
                    .user(instanceUser)
                    .build();

            when(repository.existsByDigitalObjectUuid(any())).thenReturn(false);
            when(krameriusService.getAltoBytes(PID, instance)).thenReturn(ALTO_CONTENT);
            when(objectHierarchyService.fetchAndStore(PID, instance)).thenReturn(targetObj);
            when(userService.getUserByUsername(instance)).thenReturn(instanceUser);
            when(repository.save(any(AltoVersion.class))).thenReturn(saved);

            AltoVersionWithContent result = service.createInitialVersion(PID, instance);

            assertThat(result).isNotNull();
            assertThat(result.getAltoVersion()).isEqualTo(saved);
            assertArrayEquals(ALTO_CONTENT, result.getContent());
            verify(akubraService).saveAltoContent(PID, 0, ALTO_CONTENT);
        }

        @Test
        @DisplayName("throws AltoVersionAlreadyExistsException when version already exists")
        void throws_whenVersionAlreadyExists() {
            when(repository.existsByDigitalObjectUuid(any())).thenReturn(true);

            assertThrows(AltoVersionAlreadyExistsException.class, () -> service.createInitialVersion(PID, "dk"));
        }

        @Test
        @DisplayName("throws AltoNotFoundException when Kramerius returns no ALTO")
        void throws_whenNoAltoInKramerius() {
            when(repository.existsByDigitalObjectUuid(any())).thenReturn(false);
            when(krameriusService.getAltoBytes(PID, "dk")).thenReturn(null);

            assertThrows(AltoNotFoundException.class, () -> service.createInitialVersion(PID, "dk"));
        }
    }

    @Nested
    @DisplayName("getAltoVersion")
    class GetAltoVersion {

        @Test
        @DisplayName("returns version with content when found")
        void returnsContent_whenFound() {
            DigitalObject dobj = DigitalObject.builder().pid(PID).build();
            AltoVersion av = AltoVersion.builder().digitalObject(dobj).version(VERSION).user(USER).build();
            when(repository.findByDigitalObjectUuidAndVersion(any(), eq(VERSION))).thenReturn(Optional.of(av));
            when(akubraService.retrieveDsBinaryContent(eq(PID), eq(Datastream.ALTO), eq(VERSION)))
                    .thenReturn(ALTO_CONTENT);

            AltoVersionWithContent result = service.getAltoVersion(PID, VERSION);

            assertThat(result).isNotNull();
            assertThat(result.getAltoVersion().getVersion()).isEqualTo(VERSION);
            assertArrayEquals(ALTO_CONTENT, result.getContent());
        }

        @Test
        @DisplayName("throws AltoVersionNotFoundException when version not found")
        void throws_whenNotFound() {
            when(repository.findByDigitalObjectUuidAndVersion(any(), eq(VERSION))).thenReturn(Optional.empty());

            assertThrows(AltoVersionNotFoundException.class, () -> service.getAltoVersion(PID, VERSION));
        }
    }

    @Nested
    @DisplayName("getActiveAlto")
    class GetActiveAlto {

        @Test
        @DisplayName("returns active version with content when found")
        void returnsContent_whenFound() {
            DigitalObject dobj = DigitalObject.builder().pid(PID).build();
            AltoVersion av = AltoVersion.builder().digitalObject(dobj).version(0).user(USER)
                    .state(AltoVersionState.ACTIVE).build();
            when(repository.findActive(any())).thenReturn(Optional.of(av));
            when(akubraService.retrieveDsBinaryContent(eq(PID), eq(Datastream.ALTO), eq(0)))
                    .thenReturn(ALTO_CONTENT);

            AltoVersionWithContent result = service.getActiveAlto(PID);

            assertThat(result).isNotNull();
            assertThat(result.getAltoVersion().getState()).isEqualTo(AltoVersionState.ACTIVE);
            assertArrayEquals(ALTO_CONTENT, result.getContent());
        }

        @Test
        @DisplayName("throws AltoVersionNotFoundException when no active version")
        void throws_whenNotFound() {
            when(repository.findActive(any())).thenReturn(Optional.empty());

            assertThrows(AltoVersionNotFoundException.class, () -> service.getActiveAlto(PID));
        }
    }

    @Nested
    @DisplayName("updateOrCreateVersion")
    class UpdateOrCreateVersion {

        @Test
        @DisplayName("updates existing PENDING version content")
        void updatesContent_whenPendingExists() {
            DigitalObject dobj = DigitalObject.builder().pid(PID).build();
            AltoVersion pending = AltoVersion.builder().digitalObject(dobj).version(2).user(USER)
                    .state(AltoVersionState.PENDING).build();
            when(repository.findPendingForUser(any(), eq(USER_ID))).thenReturn(Optional.of(pending));
            when(repository.save(any(AltoVersion.class))).thenReturn(pending);

            AltoVersionWithContent result = service.updateOrCreateVersion(PID, USER_ID, ALTO_CONTENT);

            assertThat(result).isNotNull();
            assertThat(result.getAltoVersion()).isEqualTo(pending);
            assertArrayEquals(ALTO_CONTENT, result.getContent());
            verify(akubraService).saveAltoContent(PID, 2, ALTO_CONTENT);
        }

        @Test
        @DisplayName("creates new PENDING version when none exists")
        void createsNewVersion_whenNoPending() {
            DigitalObject dobj = DigitalObject.builder().pid(PID).build();
            AltoVersion existing = AltoVersion.builder().digitalObject(dobj).version(1).user(USER).build();
            AltoVersion newVersion = AltoVersion.builder().digitalObject(dobj).version(2).user(USER)
                    .state(AltoVersionState.PENDING).build();

            when(repository.findPendingForUser(any(), eq(USER_ID))).thenReturn(Optional.empty());
            when(repository.findFirstByDigitalObjectUuidOrderByVersionDesc(any())).thenReturn(Optional.of(existing));
            when(repository.save(any(AltoVersion.class))).thenReturn(newVersion);

            AltoVersionWithContent result = service.updateOrCreateVersion(PID, USER_ID, ALTO_CONTENT);

            assertThat(result).isNotNull();
            assertThat(result.getAltoVersion().getVersion()).isEqualTo(2);
            assertThat(result.getAltoVersion().getState()).isEqualTo(AltoVersionState.PENDING);
            verify(akubraService).saveAltoContent(PID, 2, ALTO_CONTENT);
        }

        @Test
        @DisplayName("throws AltoVersionNotFoundException when no digital object for PID")
        void throws_whenNoDigitalObject() {
            when(repository.findPendingForUser(any(), eq(USER_ID))).thenReturn(Optional.empty());
            when(repository.findFirstByDigitalObjectUuidOrderByVersionDesc(any())).thenReturn(Optional.empty());

            assertThrows(AltoVersionNotFoundException.class,
                    () -> service.updateOrCreateVersion(PID, USER_ID, ALTO_CONTENT));
        }
    }

    @Nested
    @DisplayName("getOcr")
    class GetOcr {

        @Test
        @DisplayName("returns OCR text for version")
        void returnsOcrText() {
            int versionId = 42;
            DigitalObject dobj = DigitalObject.builder().pid(PID).build();
            AltoVersion av = AltoVersion.builder().id(Long.valueOf(versionId)).digitalObject(dobj).version(VERSION).user(USER).build();
            when(repository.findById(versionId)).thenReturn(Optional.of(av));
            when(akubraService.retrieveDsBinaryContent(eq(PID), eq(Datastream.ALTO), eq(VERSION)))
                    .thenReturn(ALTO_CONTENT);
            when(altoXmlService.convertAltoToOcr(ALTO_CONTENT)).thenReturn("extracted ocr");

            String result = service.getOcr(versionId);

            assertThat(result).isEqualTo("extracted ocr");
        }

        @Test
        @DisplayName("throws AltoVersionNotFoundException when version not found")
        void throws_whenNotFound() {
            when(repository.findById(999)).thenReturn(Optional.empty());

            assertThrows(AltoVersionNotFoundException.class, () -> service.getOcr(999));
        }
    }

    @Nested
    @DisplayName("getKrameriusObjectImage")
    class GetKrameriusObjectImage {

        @Test
        @DisplayName("delegates to KrameriusService")
        void delegatesToKrameriusService() {
            byte[] image = new byte[] { 1, 2, 3 };
            when(krameriusService.getImageBytes(PID, "dk")).thenReturn(image);

            byte[] result = service.getKrameriusObjectImage(PID, "dk");

            assertArrayEquals(image, result);
        }
    }

    @Nested
    @DisplayName("generateAlto")
    class GenerateAlto {

        @Test
        @DisplayName("creates batch and submits process when no version exists and engine enabled")
        void createsBatchAndSubmits_whenValid() {
            when(repository.existsByDigitalObjectUuid(any())).thenReturn(false);
            when(userRepository.existsEngineByUsername("tesseract")).thenReturn(true);
            when(userService.getUserById(USER_ID)).thenReturn(USER);
            Batch batch = Batch.builder().id(1).type(BatchType.GENERATE_SINGLE).pid(PID).engine("tesseract")
                    .createdBy(USER).build();
            when(batchRepository.save(any(Batch.class))).thenReturn(batch);

            Batch result = service.createGenerateAltoBatch(PID, "tesseract", BatchPriority.MEDIUM, USER_ID);

            assertThat(result).isNotNull();
            assertThat(result.getPid()).isEqualTo(PID);
            assertThat(result.getEngine()).isEqualTo("tesseract");
        }

        @Test
        @DisplayName("throws AltoVersionNotFoundException when version already exists for PID")
        void throws_whenVersionExists() {
            when(repository.existsByDigitalObjectUuid(any())).thenReturn(true);

            assertThrows(AltoVersionNotFoundException.class,
                    () -> service.createGenerateAltoBatch(PID, "tesseract", BatchPriority.MEDIUM, USER_ID));
        }

        @Test
        @DisplayName("throws IllegalArgumentException when engine not enabled")
        void throws_whenEngineNotEnabled() {
            when(repository.existsByDigitalObjectUuid(any())).thenReturn(false);
            when(userRepository.existsEngineByUsername("unknown")).thenReturn(false);

            assertThrows(IllegalArgumentException.class,
                    () -> service.createGenerateAltoBatch(PID, "unknown", BatchPriority.MEDIUM, USER_ID));
        }
    }

    @Nested
    @DisplayName("accept")
    class Accept {

        @Test
        @DisplayName("sets version to ACTIVE and archives other ACTIVE versions for same PID")
        void setsActiveAndArchivesOthers() {
            DigitalObject dobj = DigitalObject.builder().pid(PID).build();
            AltoVersion toAccept = AltoVersion.builder().id(1L).digitalObject(dobj).version(2).state(AltoVersionState.PENDING).build();
            AltoVersion otherActive = AltoVersion.builder().id(2L).digitalObject(dobj).version(1).state(AltoVersionState.ACTIVE).build();

            when(repository.findById(1)).thenReturn(Optional.of(toAccept));
            when(repository.findAllByDigitalObjectUuid(dobj.getUuid())).thenReturn(java.util.List.of(toAccept, otherActive));
            when(repository.saveAll(any())).thenReturn(java.util.List.of());

            service.accept(1);

            verify(repository).saveAll(any());
            assertThat(toAccept.getState()).isEqualTo(AltoVersionState.ACTIVE);
            assertThat(otherActive.getState()).isEqualTo(AltoVersionState.ARCHIVED);
        }
    }

    @Nested
    @DisplayName("reject")
    class Reject {

        @Test
        @DisplayName("sets PENDING version to REJECTED")
        void setsRejected_whenPending() {
            DigitalObject dobj = DigitalObject.builder().pid(PID).build();
            AltoVersion pending = AltoVersion.builder().id(1L).state(AltoVersionState.PENDING).digitalObject(dobj).build();
            when(repository.findById(1)).thenReturn(Optional.of(pending));
            when(repository.save(any(AltoVersion.class))).thenReturn(pending);

            service.reject(1);

            assertThat(pending.getState()).isEqualTo(AltoVersionState.REJECTED);
            verify(repository).save(pending);
        }

        @Test
        @DisplayName("throws IllegalStateException when version is not PENDING")
        void throws_whenNotPending() {
            AltoVersion active = AltoVersion.builder().id(1L).state(AltoVersionState.ACTIVE).build();
            when(repository.findById(1)).thenReturn(Optional.of(active));

            assertThrows(IllegalStateException.class, () -> service.reject(1));
        }

        @Test
        @DisplayName("throws when version not found")
        void throws_whenNotFound() {
            when(repository.findById(999)).thenReturn(Optional.empty());

            assertThrows(RuntimeException.class, () -> service.reject(999));
        }
    }

    @Nested
    @DisplayName("archive")
    class Archive {

        @Test
        @DisplayName("sets PENDING version to ARCHIVED")
        void setsArchived_whenPending() {
            DigitalObject dobj = DigitalObject.builder().pid(PID).build();
            AltoVersion pending = AltoVersion.builder().id(1L).state(AltoVersionState.PENDING).digitalObject(dobj).build();
            when(repository.findById(1)).thenReturn(Optional.of(pending));
            when(repository.save(any(AltoVersion.class))).thenReturn(pending);

            service.archive(1);

            assertThat(pending.getState()).isEqualTo(AltoVersionState.ARCHIVED);
            verify(repository).save(pending);
        }

        @Test
        @DisplayName("throws IllegalStateException when version is not PENDING")
        void throws_whenNotPending() {
            AltoVersion active = AltoVersion.builder().id(1L).state(AltoVersionState.ACTIVE).build();
            when(repository.findById(1)).thenReturn(Optional.of(active));

            assertThrows(IllegalStateException.class, () -> service.archive(1));
        }
    }

    @Nested
    @DisplayName("getAltoVersionUploadContent")
    class GetAltoVersionUploadContent {

        @Test
        @DisplayName("returns ALTO and OCR content for version")
        void returnsAltoAndOcrContent() {
            int versionId = 1;
            DigitalObject dobj = DigitalObject.builder().pid(PID).build();
            AltoVersion av = AltoVersion.builder().id(Long.valueOf(versionId)).digitalObject(dobj).version(VERSION).user(USER).build();
            when(repository.findById(versionId)).thenReturn(Optional.of(av));
            when(akubraService.retrieveDsBinaryContent(eq(PID), eq(Datastream.ALTO), eq(VERSION)))
                    .thenReturn(ALTO_CONTENT);
            when(akubraService.retrieveDsBinaryContent(eq(PID), eq(Datastream.TEXT_OCR), eq(VERSION)))
                    .thenReturn(OCR_CONTENT);

            AltoVersionUploadContent result = service.getAltoVersionUploadContent(versionId);

            assertThat(result).isNotNull();
            assertThat(result.getPid()).isEqualTo(PID);
            assertArrayEquals(ALTO_CONTENT, result.getAltoContent());
            assertArrayEquals(OCR_CONTENT, result.getOcrContent());
        }

        @Test
        @DisplayName("throws when version not found")
        void throws_whenNotFound() {
            when(repository.findById(999)).thenReturn(Optional.empty());

            assertThrows(RuntimeException.class, () -> service.getAltoVersionUploadContent(999));
        }
    }
}
