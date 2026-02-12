package cz.inovatika.altoEditor.domain.repository.spec;

import java.time.LocalDateTime;

import org.springframework.data.jpa.domain.Specification;

import cz.inovatika.altoEditor.domain.model.AltoVersion;

public class AltoVersionSpecification {

    public static Specification<AltoVersion> hasUser(Long userId) {
        return (root, query, cb) -> userId == null ? null : cb.equal(root.get("user_id"), userId);
    }

    public static Specification<AltoVersion> hasInstance(String instance) {
        return (root, query, cb) -> instance == null ? null : cb.equal(cb.upper(root.get("instance")), instance.toUpperCase());
    }

    public static Specification<AltoVersion> hasPid(String pid) {
        return (root, query, cb) -> pid == null ? null : cb.equal(cb.upper(root.get("pid")), pid.toUpperCase());
    }

    public static Specification<AltoVersion> hasLabelLike(String label) {
        return (root, query, cb) -> label == null ? null : cb.like(cb.upper(root.get("label")), "%" + label.toUpperCase() + "%");
    }

    public static Specification<AltoVersion> hasTitleLike(String title) {
        return (root, query, cb) -> title == null ? null : cb.like(cb.upper(root.get("title")), "%" + title.toUpperCase() + "%");
    }

    public static Specification<AltoVersion> createdAfter(LocalDateTime dateTime) {
        return (root, query, cb) -> dateTime == null ? null : cb.greaterThanOrEqualTo(root.get("created"), dateTime);
    }

    public static Specification<AltoVersion> createdBefore(LocalDateTime dateTime) {
        return (root, query, cb) -> dateTime == null ? null : cb.lessThanOrEqualTo(root.get("created"), dateTime);
    }

    public static Specification<AltoVersion> hasStateIn(Iterable<?> states) {
        return (root, query, cb) -> states == null ? null : root.get("state").in(states);
    }

}