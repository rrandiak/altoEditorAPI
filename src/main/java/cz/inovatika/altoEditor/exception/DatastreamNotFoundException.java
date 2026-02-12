package cz.inovatika.altoEditor.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class DatastreamNotFoundException extends AltoEditorException {
    public DatastreamNotFoundException(String message) {
        super(message);
    }
}