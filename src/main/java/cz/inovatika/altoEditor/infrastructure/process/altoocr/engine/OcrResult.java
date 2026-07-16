package cz.inovatika.altoEditor.infrastructure.process.altoocr.engine;

/**
 * OCR output for one page: the ALTO XML (always) and optional plain-text OCR.
 * The generate process currently persists only the ALTO; {@code ocr} may be null.
 */
public record OcrResult(byte[] alto, byte[] ocr) {
}
