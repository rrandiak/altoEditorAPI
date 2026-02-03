package cz.inovatika.altoEditor.domain.enums;

/**
 * AltoVersionState describes the review and lifecycle status of a digital object version:
 *
 * ACTIVE   - The version currently in use (accepted and uploaded to Kramerius).
 * PENDING  - Awaiting review (created by user or generator, not yet reviewed).
 * REJECTED - Reviewed and not accepted for use.
 * ARCHIVED - Old versions no longer in use. A version becomes ARCHIVED when it is
 *            replaced by a new ACTIVE version, directly archived from PENDING,
 *            or when a STALE version is resolved by syncing the ACTIVE version
 *            to its Kramerius instance.
 * STALE    - A version fetched from a Kramerius instance that differs from the
 *            current ACTIVE version. Indicates that the Kramerius instance is out
 *            of sync and needs to be updated with the ACTIVE version.
 *
 * State transitions:
 * - First version -> ACTIVE
 * - New user or generator version -> PENDING
 * - Curator accepts (uploads to Kramerius):
 *   - Accepted version, no matter the previous state -> ACTIVE
 *   - Previous ACTIVE version -> ARCHIVED
 *   - All STALE versions -> ARCHIVED
 * - Curator rejects -> REJECTED
 * - Curator directly archives PENDING version -> ARCHIVED
 * - Fetched from Kramerius:
 *   - Matching ACTIVE -> ACTIVE (no change)
 *   - Not matching ACTIVE (either from ARCHIVED or brand new) -> STALE
 * All other transitions are invalid and should be prevented.
 */
public enum AltoVersionState {
    ACTIVE,
    PENDING,
    REJECTED,
    ARCHIVED,
    STALE
}
