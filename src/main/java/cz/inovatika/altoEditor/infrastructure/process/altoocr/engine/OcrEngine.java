package cz.inovatika.altoEditor.infrastructure.process.altoocr.engine;

/**
 * Runs OCR for a single page image and returns its ALTO (and optional text).
 * Implementations: {@link ExternalProcessOcrEngine} (legacy subprocess) and
 * {@link TuzkaOcrEngine} (native tuzka-as-a-service HTTP client). {@code AltoOcrGeneratorProcess}
 * depends on this interface, not on any concrete engine.
 */
public interface OcrEngine {

    /**
     * Generate ALTO/OCR for one page.
     *
     * @param pid        the page pid (used for logging/correlation)
     * @param imageBytes the page image
     * @return the OCR result (ALTO non-null on success)
     * @throws RuntimeException if generation fails
     */
    OcrResult generate(String pid, byte[] imageBytes);
}
