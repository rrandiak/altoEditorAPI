package cz.inovatika.altoEditor.domain.enums;

/**
 * AltoVersionState describes the review and lifecycle status of a digital object version:
 *
 * ACTIVE   - The version currently in use (accepted and uploaded to Kramerius).
 * PENDING  - Awaiting review (created by user or generator, not yet reviewed).
 * REJECTED - Reviewed and not accepted for use.
 * ARCHIVED - Old active versions that have been replaced by a new ACTIVE version
 *            or version that was directly archived from PENDING. 
 *
 * State transitions:
 * - First version -> ACTIVE
 * - New user or generator version -> PENDING
 * - Curator accepts (uploads to Kramerius) -> ACTIVE (previous active becomes ARCHIVED)
 * - Curator rejects -> REJECTED
 * - Old active versions -> ARCHIVED
 * - Curator directly archives from PENDING -> ARCHIVED
 */
public enum AltoVersionState {
    ACTIVE,
    PENDING,
    REJECTED,
    ARCHIVED
}
