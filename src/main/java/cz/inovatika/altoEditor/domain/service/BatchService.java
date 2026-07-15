package cz.inovatika.altoEditor.domain.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cz.inovatika.altoEditor.domain.enums.BatchPriority;
import cz.inovatika.altoEditor.domain.enums.BatchState;
import cz.inovatika.altoEditor.domain.enums.BatchSubstate;
import cz.inovatika.altoEditor.domain.enums.BatchType;
import cz.inovatika.altoEditor.domain.model.Batch;
import cz.inovatika.altoEditor.domain.repository.BatchRepository;
import cz.inovatika.altoEditor.domain.repository.spec.BatchSpecifications;
import cz.inovatika.altoEditor.exception.BatchNotFoundException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BatchService {

    private final BatchRepository repository;

    private final UserService userService;

    public Batch getById(Integer batchId) {
    return repository.findById(batchId)
        .orElseThrow(() -> new BatchNotFoundException(batchId));
    }

    @Transactional(readOnly = true)
    public List<Batch> findWaitingBatches() {
        return repository.findByStateOrderByIdAsc(BatchState.PLANNED);
    }

    @Transactional(readOnly = true)
    public List<Batch> findRunningBatches() {
        return repository.findByStateOrderByIdAsc(BatchState.RUNNING);
    }

    /** Child stage batches of a pipeline, ordered by their stage position. */
    @Transactional(readOnly = true)
    public List<Batch> findChildStages(Integer parentBatchId) {
        return repository.findByParentBatchIdOrderByStageOrderAsc(parentBatchId);
    }

    /**
     * Claim the oldest claimable PLANNED batch of the given type: set it to RUNNING and return it.
     * Uses a lock so concurrent planners do not claim the same batch. Pipeline child stages are
     * only claimed once their earlier sibling stages are DONE. Returns empty if none claimable.
     */
    @Transactional
    public Optional<Batch> claimOldestPlannedBatchByType(BatchType type) {
        Optional<Batch> opt = repository
                .findClaimableByStateAndType(BatchState.PLANNED, type, PageRequest.of(0, 1))
                .stream().findFirst();
        opt.ifPresent(b -> setState(b, BatchState.RUNNING));
        return opt;
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
    public void setEstimatedItemCount(Batch batch, int itemCount) {
        batch.setEstimatedItemCount(itemCount);
        repository.save(batch);
    }

    @Transactional
    public void setProcessedItemCount(Batch batch, int itemCount) {
        batch.setProcessedItemCount(itemCount);
        repository.save(batch);
    }

    @Transactional
    public void setPid(Batch batch, String pid) {
        batch.setPid(pid);
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
            Long createdBy,
            Integer parentBatchId,
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
                BatchSpecifications.hasInstance(instance),
                BatchSpecifications.hasCreatedBy(createdBy),
                BatchSpecifications.hasParentBatchId(parentBatchId)
        );

        return repository.findAll(spec, pageable);
    }

    public Batch createReindexBatch(BatchPriority priority, Long userId) {
        Batch batch = repository.save(Batch.builder()
                .type(BatchType.REINDEX)
                .priority(priority)
                .createdBy(userService.getUserById(userId))
                .build());

        return batch;
    }
}