package cz.inovatika.altoEditor.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
public class WorkDirectoryCreationException extends RuntimeException {
    public WorkDirectoryCreationException(String message, Throwable cause) {
        super(message, cause);
    }
}