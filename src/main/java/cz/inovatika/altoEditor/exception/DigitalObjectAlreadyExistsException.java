package cz.inovatika.altoEditor.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class DigitalObjectAlreadyExistsException extends RuntimeException {
    public DigitalObjectAlreadyExistsException(String message) {
        super(message);
    }
}