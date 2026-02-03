package cz.inovatika.altoEditor.domain.repository.spec;

import org.springframework.data.jpa.domain.Specification;

import cz.inovatika.altoEditor.domain.model.User;

public class UserSpecifications {

    public static Specification<User> isKramerius(Boolean isKramerius) {
        return (root, query, cb) -> isKramerius == null ? null : cb.equal(root.get("isKramerius"), isKramerius);
    }
    
    public static Specification<User> isEngine(Boolean isEngine) {
        return (root, query, cb) -> isEngine == null ? null : cb.equal(root.get("isEngine"), isEngine);
    }

    public static Specification<User> isEnabled(Boolean isEnabled) {
        return (root, query, cb) -> isEnabled == null ? null : cb.equal(root.get("isEnabled"), isEnabled);
    }
}
