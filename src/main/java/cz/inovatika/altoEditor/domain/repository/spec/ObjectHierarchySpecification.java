package cz.inovatika.altoEditor.domain.repository.spec;

import org.springframework.data.jpa.domain.Specification;

import cz.inovatika.altoEditor.domain.model.AltoVersion;
import cz.inovatika.altoEditor.domain.model.DigitalObject;
import jakarta.persistence.criteria.Subquery;

public class ObjectHierarchySpecification {

    public static Specification<DigitalObject> hasPid(String pid) {
        String uuid = pid.replace("uuid:", "");
        return (root, query, cb) -> pid == null ? null : cb.equal(root.get("pid"), uuid);
    }

    public static Specification<DigitalObject> hasParent(String parentPid) {
        String parentUuid = parentPid.replace("uuid:", "");
        return (root, query, cb) -> parentPid == null ? null : cb.equal(root.get("parent").get("pid"), parentUuid);
    }

    public static Specification<DigitalObject> hasModel(String model) {
        return (root, query, cb) -> model == null ? null : cb.equal(cb.upper(root.get("model")), model.toUpperCase());
    }

    public static Specification<DigitalObject> hasTitleLike(String title) {
        return (root, query, cb) -> title == null ? null
                : cb.like(cb.upper(root.get("title")), "%" + title.toUpperCase() + "%");
    }

    public static Specification<DigitalObject> hasLevel(Integer level) {
        return (root, query, cb) -> level == null ? null : cb.equal(root.get("level"), level);
    }

    public static Specification<DigitalObject> hasAlto(Boolean hasAlto) {
        return (root, query, cb) -> {
            if (hasAlto == null)
                return null;
            Subquery<Long> subquery = query.subquery(Long.class);
            var digitalObject = subquery.from(AltoVersion.class);
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
