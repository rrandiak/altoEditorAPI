package cz.inovatika.altoEditor.domain.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

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
    private final User USER = User.builder().id(10).build();
    private final int PERO_ID = 20;
    private final int ALTOEDITOR_ID = 30;
    private final byte[] CONTENT = "alto-content".getBytes();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        Mockito.when(userService.getSpecialUser(SpecialUser.ALTOEDITOR))
                .thenReturn(User.builder().id(ALTOEDITOR_ID).build());
        Mockito.when(userService.getUserByUsername("pero"))
                .thenReturn(User.builder().id(PERO_ID).build());
    }

    @Test
    void findRelatedAlto_bySpecificVersion() {
        DigitalObject obj = DigitalObject.builder().pid(PID).version(VERSION).user(USER).build();
        Mockito.when(repository.findRelated(eq(PID), eq(USER.getId())))
                .thenReturn(Optional.of(obj));
        Mockito.when(akubraService.retrieveDsBinaryContent(eq(PID), any(), eq(VERSION))).thenReturn(CONTENT);

        DigitalObjectWithContent result = service.findRelatedAlto(PID, USER.getId());
        assertNotNull(result);
        assertEquals(obj, result.getDigitalObject());
        assertArrayEquals(CONTENT, result.getContent());
    }
}
