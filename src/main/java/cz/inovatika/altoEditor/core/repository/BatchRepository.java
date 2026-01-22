package cz.inovatika.altoEditor.core.repository;

import cz.inovatika.altoEditor.core.entity.Batch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BatchRepository extends JpaRepository<Batch, Integer>,
        JpaSpecificationExecutor<Batch> {

    List<Batch> findByStateOrderByIdAsc(String state);

    void setFailedForAllRunningBatches(String log);
}
