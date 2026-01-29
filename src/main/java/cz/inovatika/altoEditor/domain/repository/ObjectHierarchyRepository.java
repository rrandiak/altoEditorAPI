package cz.inovatika.altoEditor.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import cz.inovatika.altoEditor.domain.model.ObjectHierarchyNode;

@Repository
public interface ObjectHierarchyRepository
                extends JpaRepository<ObjectHierarchyNode, Integer>,
                JpaSpecificationExecutor<ObjectHierarchyNode> {

}
