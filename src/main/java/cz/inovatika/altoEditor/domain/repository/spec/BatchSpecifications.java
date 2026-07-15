package cz.inovatika.altoEditor.domain.repository.spec;

import java.time.LocalDateTime;

import org.springframework.data.jpa.domain.Specification;

import cz.inovatika.altoEditor.domain.enums.BatchPriority;
import cz.inovatika.altoEditor.domain.enums.BatchState;
import cz.inovatika.altoEditor.domain.enums.BatchSubstate;
import cz.inovatika.altoEditor.domain.enums.BatchType;
import cz.inovatika.altoEditor.domain.model.Batch;

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
        return (root, query, cb) -> date == null ? null : cb.greaterThanOrEqualTo(root.get("createdAt"), date);
    }

    public static Specification<Batch> createdBefore(LocalDateTime date) {
        return (root, query, cb) -> date == null ? null : cb.lessThanOrEqualTo(root.get("createdAt"), date);
    }

    public static Specification<Batch> updatedAfter(LocalDateTime date) {
        return (root, query, cb) -> date == null ? null : cb.greaterThanOrEqualTo(root.get("updatedAt"), date);
    }

    public static Specification<Batch> updatedBefore(LocalDateTime date) {
        return (root, query, cb) -> date == null ? null : cb.lessThanOrEqualTo(root.get("updatedAt"), date);
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

    public static Specification<Batch> hasCreatedBy(Long userId) {
        return (root, query, cb) -> userId == null ? null : cb.equal(root.get("createdBy").get("id"), userId);
    }

    public static Specification<Batch> hasParentBatchId(Integer parentBatchId) {
        return (root, query, cb) -> parentBatchId == null ? null : cb.equal(root.get("parentBatchId"), parentBatchId);
    }
}