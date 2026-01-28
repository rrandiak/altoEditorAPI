package cz.inovatika.altoEditor.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
public class AkubraStorageException extends RuntimeException {
    public AkubraStorageException(String message, Throwable cause) {
        super(message, cause);
    }
    public AkubraStorageException(Throwable cause) {
        super(cause);
    }
}