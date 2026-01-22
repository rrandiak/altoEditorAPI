package cz.inovatika.altoEditor.core.repository.spec;

import cz.inovatika.altoEditor.core.entity.Batch;
import cz.inovatika.altoEditor.core.enums.BatchPriority;
import cz.inovatika.altoEditor.core.enums.BatchState;
import cz.inovatika.altoEditor.core.enums.BatchSubstate;
import cz.inovatika.altoEditor.core.enums.BatchType;

import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;

public class BatchSpecifications {

    public static Specification<Batch> hasPid(String pid) {
        return (root, query, cb) -> pid == null ? null : cb.equal(root.get("pid"), pid);
    }

    public static Specification<Batch> hasState(BatchState state) {
        return (root, query, cb) -> state == null ? null : cb.equal(root.get("state"), state);
    }

    public static Specification<Batch> hasSubstate(BatchSubstate substate) {
        return (root, query, cb) -> substate == null ? null
                : cb.equal(root.get("substate"), substate);
    }

    public static Specification<Batch> createdAfter(LocalDateTime date) {
        return (root, query, cb) -> date == null ? null : cb.greaterThanOrEqualTo(root.get("create_date"), date);
    }

    public static Specification<Batch> createdBefore(LocalDateTime date) {
        return (root, query, cb) -> date == null ? null : cb.lessThanOrEqualTo(root.get("create_date"), date);
    }

    public static Specification<Batch> updatedAfter(LocalDateTime date) {
        return (root, query, cb) -> date == null ? null : cb.greaterThanOrEqualTo(root.get("update_date"), date);
    }

    public static Specification<Batch> updatedBefore(LocalDateTime date) {
        return (root, query, cb) -> date == null ? null : cb.lessThanOrEqualTo(root.get("update_date"), date);
    }

    public static Specification<Batch> hasPriority(BatchPriority priority) {
        return (root, query, cb) -> priority == null ? null : cb.equal(root.get("priority"), priority);
    }

    public static Specification<Batch> hasType(BatchType type) {
        return (root, query, cb) -> type == null ? null : cb.equal(root.get("type"), type);
    }

    public static Specification<Batch> hasInstance(String instance) {
        return (root, query, cb) -> instance == null ? null : cb.equal(root.get("instance"), instance);
    }
}