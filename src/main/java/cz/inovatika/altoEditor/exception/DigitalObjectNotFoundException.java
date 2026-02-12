package cz.inovatika.altoEditor.exception;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class DigitalObjectNotFoundException extends AltoEditorException {
    public DigitalObjectNotFoundException(String message) {
        super(message);
    }

    public DigitalObjectNotFoundException(UUID uuid) {
        super("Digital object with PID uuid:" + uuid + " not found.");
    }
}