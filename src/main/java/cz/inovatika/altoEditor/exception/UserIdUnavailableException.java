package cz.inovatika.altoEditor.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.UNAUTHORIZED)
public class UserIdUnavailableException extends AltoEditorException {
    public UserIdUnavailableException(String message) {
        super(message);
    }
}