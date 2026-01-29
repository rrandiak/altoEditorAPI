package cz.inovatika.altoEditor.domain.repository.spec;

import org.springframework.data.jpa.domain.Specification;

import cz.inovatika.altoEditor.domain.model.DigitalObject;
import cz.inovatika.altoEditor.domain.model.ObjectHierarchyNode;
import jakarta.persistence.criteria.Subquery;

public class ObjectHierarchySpecification {

    public static Specification<ObjectHierarchyNode> hasPid(String pid) {
        String uuid = pid.replace("uuid:", "");
        return (root, query, cb) -> pid == null ? null : cb.equal(root.get("pid"), uuid);
    }

    public static Specification<ObjectHierarchyNode> hasParent(String parentPid) {
        String parentUuid = parentPid.replace("uuid:", "");
        return (root, query, cb) -> parentPid == null ? null : cb.equal(root.get("parent").get("pid"), parentUuid);
    }

    public static Specification<ObjectHierarchyNode> hasModel(String model) {
        return (root, query, cb) -> model == null ? null : cb.equal(cb.upper(root.get("model")), model.toUpperCase());
    }

    public static Specification<ObjectHierarchyNode> hasTitleLike(String title) {
        return (root, query, cb) -> title == null ? null
                : cb.like(cb.upper(root.get("title")), "%" + title.toUpperCase() + "%");
    }

    public static Specification<ObjectHierarchyNode> hasLevel(Short level) {
        return (root, query, cb) -> level == null ? null : cb.equal(root.get("level"), level);
    }

    public static Specification<ObjectHierarchyNode> hasAlto(Boolean hasAlto) {
        return (root, query, cb) -> {
            if (hasAlto == null)
                return null;
            Subquery<Long> subquery = query.subquery(Long.class);
            var digitalObject = subquery.from(DigitalObject.class);
            subquery.select(cb.literal(1L))
                    .where(cb.equal(digitalObject.get("hierarchyNode"), root));
            if (hasAlto) {
                return cb.exists(subquery);
            } else {
                return cb.not(cb.exists(subquery));
            }
        };
    }
}
