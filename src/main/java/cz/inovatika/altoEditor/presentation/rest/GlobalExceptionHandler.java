package cz.inovatika.altoEditor.presentation.rest;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.HttpStatusCodeException;

import cz.inovatika.altoEditor.exception.AltoEditorException;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.extern.slf4j.Slf4j;

/**
 * Handles exceptions from REST controllers so the client gets the correct HTTP status
 * and a JSON body. Domain exceptions (not found, conflict, bad request) and upstream
 * (Kramerius) errors are mapped here. 401/403 are handled by SecurityConfig.
 */
@RestControllerAdvice
@Hidden
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(AltoEditorException.class)
    public ResponseEntity<Map<String, Object>> handleAltoEditorException(AltoEditorException ex) {
        Map<String, Object> errorBody = Map.of(
                "status", HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "message", ex.getMessage());

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorBody);
    }

    @ExceptionHandler(HttpStatusCodeException.class)
    public ResponseEntity<Map<String, Object>> handleUpstreamHttpError(HttpStatusCodeException ex) {
        HttpStatus upstreamStatus = HttpStatus.resolve(ex.getStatusCode().value());
        int statusCode = ex.getStatusCode().value();
        String body = ex.getResponseBodyAsString();

        log.warn("Upstream HTTP error {}: {}", statusCode, ex.getMessage());

        HttpStatus responseStatus = (statusCode >= 500 && statusCode < 600)
                ? HttpStatus.BAD_GATEWAY
                : HttpStatus.valueOf(statusCode);

        String bodySnippet = (body != null && !body.isBlank())
                ? (body.length() > 2000 ? body.substring(0, 2000) + "..." : body)
                : "(no body)";

        Map<String, Object> errorBody = Map.of(
                "status", responseStatus.value(),
                "upstreamStatus", statusCode,
                "message", upstreamStatus != null ? upstreamStatus.getReasonPhrase() : "Upstream error",
                "upstreamBody", bodySnippet);

        return ResponseEntity.status(responseStatus).body(errorBody);
    }
}
