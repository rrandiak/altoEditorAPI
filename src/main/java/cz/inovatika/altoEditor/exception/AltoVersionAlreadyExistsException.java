package cz.inovatika.altoEditor.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class AltoVersionAlreadyExistsException extends RuntimeException {
    public AltoVersionAlreadyExistsException(String message) {
        super(message);
    }
}