package cz.inovatika.altoEditor.domain.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import cz.inovatika.altoEditor.domain.model.ObjectHierarchyNode;
import cz.inovatika.altoEditor.domain.repository.ObjectHierarchyRepository;
import cz.inovatika.altoEditor.domain.repository.spec.ObjectHierarchySpecification;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ObjectHierarchyService {

    private final ObjectHierarchyRepository repository;

    public Page<ObjectHierarchyNode> search(String pid, String parentPid, String model, String title, Short level, Boolean hasAlto,
            Pageable pageable) {
        return repository.findAll(Specification.allOf(
                ObjectHierarchySpecification.hasPid(pid),
                ObjectHierarchySpecification.hasParent(parentPid),
                ObjectHierarchySpecification.hasModel(model),
                ObjectHierarchySpecification.hasTitleLike(title),
                ObjectHierarchySpecification.hasLevel(level),
                ObjectHierarchySpecification.hasAlto(hasAlto))
                , pageable);
    }

}
