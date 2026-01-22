package cz.inovatika.altoEditor.core.service;

import cz.inovatika.altoEditor.core.entity.Batch;
import cz.inovatika.altoEditor.core.enums.BatchPriority;
import cz.inovatika.altoEditor.core.enums.BatchState;
import cz.inovatika.altoEditor.core.enums.BatchSubstate;
import cz.inovatika.altoEditor.core.enums.BatchType;
import cz.inovatika.altoEditor.core.repository.BatchRepository;
import cz.inovatika.altoEditor.core.repository.spec.BatchSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BatchService {

    private final BatchRepository repository;

    @Transactional(readOnly = true)
    public Batch getById(Integer id) {
        return repository.findById(id).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<Batch> findWaitingBatches() {
        return repository.findByStateOrderByIdAsc("PLANNED");
    }

    @Transactional(readOnly = true)
    public List<Batch> findRunningBatches() {
        return repository.findByStateOrderByIdAsc("RUNNING");
    }

    @Transactional
    public void setState(Batch batch, BatchState state) {
        batch.setState(state);
        repository.save(batch);
    }

    @Transactional
    public void setSubstate(Batch batch, BatchSubstate substate) {
        batch.setSubstate(substate);
        repository.save(batch);
    }

    @Transactional
    public void setFailed(Batch batch, String reason) {
        batch.setState(BatchState.FAILED);
        batch.setLog(reason);
        repository.save(batch);
    }

    @Transactional
    public void setRunInfo(Batch batch, Integer estimatedItemCount, BatchType type) {
        batch.setEstimatedItemCount(estimatedItemCount);
        batch.setType(type);
        repository.save(batch);
    }

    @Transactional(readOnly = true)
    public Page<Batch> search(String pid,
            BatchState state,
            BatchSubstate substate,
            LocalDateTime createdAfter,
            LocalDateTime createdBefore,
            LocalDateTime updatedAfter,
            LocalDateTime updatedBefore,
            BatchPriority priority,
            BatchType type,
            String instance,
            Pageable pageable) {
        Specification<Batch> spec = Specification.allOf(
                BatchSpecifications.hasPid(pid),
                BatchSpecifications.hasState(state),
                BatchSpecifications.hasSubstate(substate),
                BatchSpecifications.createdAfter(createdAfter),
                BatchSpecifications.updatedAfter(updatedAfter),
                BatchSpecifications.createdBefore(createdBefore),
                BatchSpecifications.updatedBefore(updatedBefore),
                BatchSpecifications.hasPriority(priority),
                BatchSpecifications.hasType(type),
                BatchSpecifications.hasInstance(instance)
        );

        return repository.findAll(spec, pageable);
    }
}