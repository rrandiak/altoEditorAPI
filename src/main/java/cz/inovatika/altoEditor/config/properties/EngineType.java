package cz.inovatika.altoEditor.config.properties;

/**
 * Which OCR engine implementation backs a configured engine.
 */
public enum EngineType {
    /** Legacy external subprocess (e.g. pero-vut, pero-distributed Python clients). */
    SUBPROCESS,
    /** Native tuzka-as-a-service HTTP client. */
    TUZKA,
}
