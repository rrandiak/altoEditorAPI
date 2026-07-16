package cz.inovatika.altoEditor.infrastructure.process.altoocr.engine.tuzka;

/**
 * Coarse lifecycle state of a taas job, abstracted from the raw taas status strings
 * ({@code queued}/{@code running} → RUNNING, {@code done} → DONE, {@code failed} → FAILED).
 */
public enum TuzkaJobState {
    RUNNING,
    DONE,
    FAILED;

    /** Map a raw taas status string to a coarse state; unknown values are treated as RUNNING. */
    public static TuzkaJobState fromStatus(String status) {
        if (status == null) {
            return RUNNING;
        }
        return switch (status.toLowerCase()) {
            case "done", "finished", "succeeded" -> DONE;
            case "failed", "error", "killed" -> FAILED;
            default -> RUNNING;
        };
    }
}
