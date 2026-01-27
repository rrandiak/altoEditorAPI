package cz.inovatika.altoEditor.domain.repository.spec;

import java.time.LocalDateTime;

import org.springframework.data.jpa.domain.Specification;

import cz.inovatika.altoEditor.domain.model.DigitalObject;

public class DigitalObjectSpecifications {

    public static Specification<DigitalObject> hasUser(Integer userId) {
        return (root, query, cb) -> userId == null ? null : cb.equal(root.get("user_id"), userId);
    }

    public static Specification<DigitalObject> hasInstance(String instanceId) {
        return (root, query, cb) -> instanceId == null ? null : cb.equal(cb.upper(root.get("instance_id")), instanceId.toUpperCase());
    }

    public static Specification<DigitalObject> hasPid(String pid) {
        return (root, query, cb) -> pid == null ? null : cb.equal(cb.upper(root.get("pid")), pid.toUpperCase());
    }

    public static Specification<DigitalObject> hasLabelLike(String label) {
        return (root, query, cb) -> label == null ? null : cb.like(cb.upper(root.get("label")), "%" + label.toUpperCase() + "%");
    }

    public static Specification<DigitalObject> hasTitleLike(String title) {
        return (root, query, cb) -> title == null ? null : cb.like(cb.upper(root.get("title")), "%" + title.toUpperCase() + "%");
    }

    public static Specification<DigitalObject> createdAfter(LocalDateTime dateTime) {
        return (root, query, cb) -> dateTime == null ? null : cb.greaterThanOrEqualTo(root.get("created"), dateTime);
    }

    public static Specification<DigitalObject> createdBefore(LocalDateTime dateTime) {
        return (root, query, cb) -> dateTime == null ? null : cb.lessThanOrEqualTo(root.get("created"), dateTime);
    }

    public static Specification<DigitalObject> hasStateIn(Iterable<?> states) {
        return (root, query, cb) -> states == null ? null : root.get("state").in(states);
    }

}