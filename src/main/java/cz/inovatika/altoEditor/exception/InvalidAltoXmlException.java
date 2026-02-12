package cz.inovatika.altoEditor.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidAltoXmlException extends AltoEditorException {
    public InvalidAltoXmlException(String message) {
        super(message);
    }
    public InvalidAltoXmlException(String message, Throwable cause) {
        super(message, cause);
    }
}
