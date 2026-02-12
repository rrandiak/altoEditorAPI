package cz.inovatika.altoEditor.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class BatchNotFoundException extends AltoEditorException {
    public BatchNotFoundException(Integer batchId) {
        super("Batch with ID " + batchId + " not found.");
    }
}
