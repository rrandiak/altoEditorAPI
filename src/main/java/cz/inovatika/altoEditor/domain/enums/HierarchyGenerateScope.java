package cz.inovatika.altoEditor.domain.enums;

/**
 * Scope for hierarchy ALTO generation: which pages to include.
 * Used in {@link cz.inovatika.altoEditor.domain.model.dto.HierarchyGenerateInput} and stored in {@link cz.inovatika.altoEditor.domain.model.Batch#data}.
 */
public enum HierarchyGenerateScope {
    /** All target documents in the hierarchy. */
    ALL,
    /** Only documents that do not have a PENDING version for the engine. */
    NO_PENDING,
    /** Only documents that have neither PENDING nor ACTIVE version for the engine. */
    NO_PENDING_NOR_ACTIVE,
}
