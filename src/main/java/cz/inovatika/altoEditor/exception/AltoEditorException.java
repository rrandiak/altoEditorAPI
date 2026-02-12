package cz.inovatika.altoEditor.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
public class AltoEditorException extends RuntimeException {
    public AltoEditorException(String message) {
        super(message);
    }
    public AltoEditorException(String message, Throwable cause) {
        super(message, cause);
    }
    public AltoEditorException(Throwable cause) {
        super(cause);
    }
}
