package cz.inovatika.altoEditor.core.repository.spec;

import org.springframework.data.jpa.domain.Specification;

import cz.inovatika.altoEditor.core.entity.DigitalObject;

public class DigitalObjectSpecifications {

    public static Specification<DigitalObject> hasPid(String pid) {
        return (root, query, cb) -> pid == null ? null : cb.equal(cb.upper(root.get("pid")), pid.toUpperCase());
    }

    public static Specification<DigitalObject> hasUser(Integer userId) {
        return (root, query, cb) -> userId == null ? null : cb.equal(root.get("r_user_id"), userId);
    }

}