package cz.inovatika.altoEditor.domain.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import cz.inovatika.altoEditor.domain.enums.SpecialUser;
import cz.inovatika.altoEditor.domain.model.DigitalObject;
import cz.inovatika.altoEditor.domain.model.User;
import cz.inovatika.altoEditor.domain.repository.DigitalObjectRepository;
import cz.inovatika.altoEditor.domain.repository.UserRepository;
import cz.inovatika.altoEditor.domain.service.container.DigitalObjectWithContent;
import cz.inovatika.altoEditor.infrastructure.editor.AltoXmlService;
import cz.inovatika.altoEditor.infrastructure.kramerius.KrameriusService;
import cz.inovatika.altoEditor.infrastructure.storage.AkubraService;

class DigitalObjectServiceTest {
    @Mock
    private DigitalObjectRepository repository;
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
    private DigitalObjectService service;

    private final String PID = "pid1";
    private final int VERSION = 1;
    private final int USER_ID = 10;
    private final int PERO_ID = 20;
    private final int ALTOEDITOR_ID = 30;
    private final byte[] CONTENT = "alto-content".getBytes();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        Mockito.when(userService.getSpecialUser(SpecialUser.PERO))
                .thenReturn(User.builder().id(PERO_ID).build());
        Mockito.when(userService.getSpecialUser(SpecialUser.ALTOEDITOR))
                .thenReturn(User.builder().id(ALTOEDITOR_ID).build());
    }

    @Test
    void findAlto_bySpecificVersion() {
        DigitalObject obj = DigitalObject.builder().pid(PID).version(VERSION).userId(USER_ID).build();
        Mockito.when(repository.findByPidAndVersionAndUsersWithPriority(eq(PID), eq(VERSION), eq(USER_ID), anyInt(),
                anyInt()))
                .thenReturn(Optional.of(obj));
        Mockito.when(akubraService.retrieveDsBinaryContent(eq(PID), any(), eq(VERSION))).thenReturn(CONTENT);

        DigitalObjectWithContent result = service.findAlto(PID, VERSION, USER_ID);
        assertNotNull(result);
        assertEquals(obj, result.getDigitalObject());
        assertArrayEquals(CONTENT, result.getContent());
    }

    @Test
    void findAlto_byUser() {
        DigitalObject obj = DigitalObject.builder().pid(PID).version(VERSION).userId(USER_ID).build();
        Mockito.when(
                repository.findByPidAndVersionAndUsersWithPriority(eq(PID), isNull(), eq(USER_ID), anyInt(), anyInt()))
                .thenReturn(Optional.of(obj));
        Mockito.when(akubraService.retrieveDsBinaryContent(eq(PID), any(), eq(VERSION))).thenReturn(CONTENT);

        DigitalObjectWithContent result = service.findAlto(PID, null, USER_ID);
        assertNotNull(result);
        assertEquals(obj, result.getDigitalObject());
        assertArrayEquals(CONTENT, result.getContent());
    }

    @Test
    void findAlto_byPeroUser() {
        DigitalObject obj = DigitalObject.builder().pid(PID).version(VERSION).userId(PERO_ID).build();
        Mockito.when(repository.findByPidAndVersionAndUsersWithPriority(eq(PID), isNull(), eq(USER_ID), eq(PERO_ID),
                anyInt()))
                .thenReturn(Optional.of(obj));
        Mockito.when(akubraService.retrieveDsBinaryContent(eq(PID), any(), eq(VERSION))).thenReturn(CONTENT);

        DigitalObjectWithContent result = service.findAlto(PID, null, USER_ID);
        assertNotNull(result);
        assertEquals(obj, result.getDigitalObject());
        assertArrayEquals(CONTENT, result.getContent());
    }

    @Test
    void findAlto_byAltoEditorUser() {
        DigitalObject obj = DigitalObject.builder().pid(PID).version(VERSION).userId(ALTOEDITOR_ID).build();
        Mockito.when(repository.findByPidAndVersionAndUsersWithPriority(eq(PID), isNull(), eq(USER_ID), eq(PERO_ID),
                eq(ALTOEDITOR_ID)))
                .thenReturn(Optional.of(obj));
        Mockito.when(akubraService.retrieveDsBinaryContent(eq(PID), any(), eq(VERSION))).thenReturn(CONTENT);

        DigitalObjectWithContent result = service.findAlto(PID, null, USER_ID);
        assertNotNull(result);
        assertEquals(obj, result.getDigitalObject());
        assertArrayEquals(CONTENT, result.getContent());
    }

    @Test
    void findAlto_returnsNullIfNotFound() {
        Mockito.when(repository.findByPidAndVersionAndUsersWithPriority(any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(Optional.empty());
        DigitalObjectWithContent result = service.findAlto(PID, VERSION, USER_ID);
        assertNull(result);
    }
}
