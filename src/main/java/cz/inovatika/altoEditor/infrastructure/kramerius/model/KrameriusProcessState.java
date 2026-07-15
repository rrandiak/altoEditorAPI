package cz.inovatika.altoEditor.infrastructure.kramerius.model;

/**
 * Coarse lifecycle state of a planned Kramerius admin process, abstracted from
 * the concrete K7 process states so callers can poll for completion.
 */
public enum KrameriusProcessState {
    /** Planned or running — not yet in a terminal state; keep polling. */
    RUNNING,
    /** Terminal success (K7 FINISHED / WARNING). */
    FINISHED,
    /** Terminal failure (K7 FAILED / KILLED). */
    FAILED;
}
