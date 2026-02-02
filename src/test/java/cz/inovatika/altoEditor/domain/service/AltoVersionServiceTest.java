package cz.inovatika.altoEditor.domain.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import cz.inovatika.altoEditor.domain.enums.Datastream;
import cz.inovatika.altoEditor.domain.enums.SpecialUser;
import cz.inovatika.altoEditor.domain.model.AltoVersion;
import cz.inovatika.altoEditor.domain.model.DigitalObject;
import cz.inovatika.altoEditor.domain.model.User;
import cz.inovatika.altoEditor.domain.repository.AltoVersionRepository;
import cz.inovatika.altoEditor.domain.repository.UserRepository;
import cz.inovatika.altoEditor.domain.service.container.AltoVersionWithContent;
import cz.inovatika.altoEditor.infrastructure.editor.AltoXmlService;
import cz.inovatika.altoEditor.infrastructure.kramerius.KrameriusService;
import cz.inovatika.altoEditor.infrastructure.storage.AkubraService;

class AltoVersionServiceTest {
    @Mock
    private AltoVersionRepository repository;
    @Mock
    private UserService userService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AkubraService akubraService;
    @Mock
    private KrameriusService krameriusService;
    @Mock
    private AltoXmlService altoXmlService;

    @InjectMocks
    private AltoVersionService service;

    private final UUID TEST_UUID = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
    private final String PID = "uuid:" + TEST_UUID.toString();
    private final int VERSION = 1;
    private final User USER = User.builder().id(10L).build();
    private final long PERO_ID = 20;
    private final long ALTOEDITOR_ID = 30;
    private final byte[] CONTENT = "alto-content".getBytes();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        Mockito.when(userService.getSpecialUser(SpecialUser.KRAMERIUS))
                .thenReturn(User.builder().id(ALTOEDITOR_ID).build());
        Mockito.when(userService.getUserByUsername("pero"))
                .thenReturn(User.builder().id(PERO_ID).build());
    }

    @Test
    void findRelatedAlto_bySpecificVersion() {
        DigitalObject dobj = DigitalObject.builder().uuid(TEST_UUID).build();
        AltoVersion obj = AltoVersion.builder().digitalObject(dobj).version(VERSION).user(USER).build();
        Mockito.when(repository.findRelated(eq(TEST_UUID), eq(USER.getId())))
                .thenReturn(Optional.of(obj));
        Mockito.when(akubraService.retrieveDsBinaryContent(eq(PID), any(), eq(VERSION))).thenReturn(CONTENT);

        AltoVersionWithContent result = service.findRelatedAlto(PID, USER.getId());
        assertNotNull(result);
        assertEquals(obj, result.getAltoVersion());
        assertArrayEquals(CONTENT, result.getContent());
    }

    @Test
    void fetchNewAlto_createsNewAltoVersionAndReturnsContent() {
        String pid = "uuid:123e4567-e89b-12d3-a456-426614174000";
        String instance = "inst1";
        Long userId = 42L;
        String token = "tok";
        byte[] foxml = new byte[] { 1, 2, 3 };
        byte[] alto = new byte[] { 4, 5, 6 };
        User owner = User.builder().id(userId).build();
        DigitalObject dobj = DigitalObject.builder().pid(pid).build();
        AltoVersion saved = AltoVersion.builder().digitalObject(dobj).instance(instance).version(0).user(owner).build();

        when(repository.existsByDigitalObjectUuid(dobj.getUuid())).thenReturn(false);
        when(krameriusService.getFoxmlBytes(pid, instance, token)).thenReturn(foxml);
        when(akubraService.getLatestDsVersionBinaryContent(foxml, Datastream.ALTO)).thenReturn(alto);
        when(repository.findFirstByDigitalObjectUuidOrderByVersionDesc(dobj.getUuid())).thenReturn(Optional.empty());
        when(userRepository.findById(userId)).thenReturn(Optional.of(owner));
        when(repository.save(any(AltoVersion.class))).thenReturn(saved);
        when(akubraService.retrieveDsBinaryContent(pid, Datastream.ALTO, 0)).thenReturn(alto);

        var result = service.fetchNewAlto(pid, instance, userId, token);
        assertNotNull(result);
        assertEquals(saved, result.getAltoVersion());
        assertArrayEquals(alto, result.getContent());
    }

    @Test
    void fetchNewAlto_throwsIfExists() {
        when(repository.existsByDigitalObjectUuid(TEST_UUID)).thenReturn(true);
        assertThrows(RuntimeException.class, () -> service.fetchNewAlto("pid1", "inst", 1L, "tok"));
    }

    @Test
    void fetchNewAlto_throwsIfNoAlto() {
        String pid = "pid1";
        String instanceId = "inst1";
        Long userId = 42L;
        String token = "tok";
        byte[] foxml = new byte[] { 1, 2, 3 };
        when(repository.existsByDigitalObjectUuid(TEST_UUID)).thenReturn(false);
        when(krameriusService.getFoxmlBytes(pid, instanceId, token)).thenReturn(foxml);
        when(akubraService.getLatestDsVersionBinaryContent(foxml, Datastream.ALTO)).thenReturn(null);
        assertThrows(RuntimeException.class, () -> service.fetchNewAlto(pid, instanceId, userId, token));
    }
}
