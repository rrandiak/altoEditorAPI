package cz.inovatika.altoEditor.domain.service;

import java.util.List;
import java.util.Optional;
import java.util.Stack;
import java.util.UUID;

import org.hibernate.search.engine.search.query.SearchResult;
import org.hibernate.search.engine.search.sort.dsl.SortOrder;
import org.hibernate.search.mapper.orm.Search;
import org.hibernate.search.mapper.orm.session.SearchSession;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import cz.inovatika.altoEditor.domain.adapter.PidAdapter;
import cz.inovatika.altoEditor.domain.enums.BatchPriority;
import cz.inovatika.altoEditor.domain.enums.BatchType;
import cz.inovatika.altoEditor.domain.model.Batch;
import cz.inovatika.altoEditor.domain.model.DigitalObject;
import cz.inovatika.altoEditor.domain.enums.HierarchyGenerateScope;
import cz.inovatika.altoEditor.domain.model.dto.HierarchyGenerateInput;
import cz.inovatika.altoEditor.domain.model.dto.PageCountStats;
import cz.inovatika.altoEditor.domain.repository.BatchRepository;
import cz.inovatika.altoEditor.domain.repository.DigitalObjectRepository;
import cz.inovatika.altoEditor.exception.DigitalObjectNotFoundException;
import cz.inovatika.altoEditor.infrastructure.kramerius.KrameriusService;
import cz.inovatika.altoEditor.infrastructure.kramerius.model.KrameriusObjectMetadata;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ObjectHierarchyService {

    private final DigitalObjectRepository digitalObjectRepository;

    private final KrameriusService krameriusService;

    private final EntityManager entityManager;

    private final UserService userService;

    private final BatchRepository batchRepository;

    private final ObjectMapper objectMapper;

    public SearchResult<DigitalObject> search(String pid, String parentPid, String model, String title, Integer level,
            int offset, int limit, String sortBy, SortOrder sortOrder) {
        SearchSession session = Search.session(entityManager);

        boolean hasFilter = pid != null || parentPid != null || model != null || title != null || level != null;

        var searchQuery = session.search(DigitalObject.class)
                .where(f -> {
                    var bool = f.bool();
                    if (!hasFilter) {
                        bool.must(f.matchAll());
                    } else {
                        if (pid != null) {
                            bool.must(f.match().field("pid").matching(pid));
                        }
                        if (parentPid != null) {
                            bool.must(f.match().field("parentPid").matching(parentPid));
                        }
                        if (model != null) {
                            bool.must(f.match().field("model").matching(model));
                        }
                        if (title != null) {
                            bool.must(f.match().field("title").matching(title));
                        }
                        if (level != null) {
                            bool.must(f.match().field("level").matching(level));
                        }
                    }
                    return bool;
                });
        
        if (sortBy != null) {
            String sortField = "title".equals(sortBy) ? "title_sort" : sortBy;
            searchQuery.sort(f -> f.field(sortField).order(sortOrder));
        }

        return searchQuery.fetch(offset, limit);
    }

    /**
     * All page PIDs under the given ancestor from object_hierarchy (recursive).
     */
    @Transactional
    public List<String> findDescendantPagePids(String ancestorPid) {
        UUID ancestorUuid = PidAdapter.toUuid(ancestorPid);
        return digitalObjectRepository.findDescendantPageUuids(ancestorUuid).stream()
                .map(uuid -> "uuid:" + uuid)
                .toList();
    }

    /**
     * Fetches the hierarchy from Kramerius and stores it in local database.
     * Starts from the given PID (leaf = model PAGE) and goes up to the root.
     * 
     * In traversing the hierarchy, creates DigitalObject entries as needed.
     * If the DigitalObject for a given PID already exists, it is not created again
     * and is used as parent for the lower level objects.
     * 
     * While creating DigitalObjects, the parent-child relationships are set
     * by providing the parent DigitalObject when creating a child DigitalObject.
     * 
     * The returned DigitalObject is the one for the given PID.
     * 
     * @param pid
     * @param instance
     * @return
     */
    @Transactional
    public DigitalObject fetchAndStore(String pid, String instance) {
        // Check if already present
        Optional<DigitalObject> existing = digitalObjectRepository.findById(PidAdapter.toUuid(pid));
        if (existing.isPresent()) {
            return existing.get();
        }

        Stack<KrameriusObjectMetadata> stack = new Stack<>();
        String currentPid = pid;
        DigitalObject parent = null;

        while (true) {
            KrameriusObjectMetadata metadata = krameriusService.getObjectMetadata(currentPid, instance);
            if (metadata == null) {
                throw new DigitalObjectNotFoundException(PidAdapter.toUuid(currentPid));
            }

            // Check if parent exists in DB
            String parentPid = metadata.getParentPid();
            parent = (parentPid != null)
                    ? digitalObjectRepository.findById(PidAdapter.toUuid(parentPid)).orElse(null)
                    : null;

            stack.push(metadata);
            if (parent != null || parentPid == null) {
                break;
            }
            currentPid = parentPid;
        }

        // Now unwind stack and create DigitalObjects from root to leaf
        while (!stack.isEmpty()) {
            KrameriusObjectMetadata metadata = stack.pop();
            parent = digitalObjectRepository.save(DigitalObject.builder()
                    .pid(metadata.getPid())
                    .model(metadata.getModel())
                    .title(metadata.getTitle())
                    .level(metadata.getLevel())
                    .indexInParent(metadata.getIndexInParent())
                    .parent(parent)
                    .build());
        }

        refreshPageCountsForAncestors(parent.getUuid());
        return parent;
    }

    @Transactional
    public DigitalObject store(KrameriusObjectMetadata metadata) {
        Optional<DigitalObject> existing = digitalObjectRepository.findById(metadata.getUuid());
        if (existing.isPresent()) {
            return existing.get();
        }

        DigitalObject parent = metadata.getParentPid() != null
                ? digitalObjectRepository.findById(metadata.getParentUuid())
                        .orElseThrow(() -> new DigitalObjectNotFoundException(metadata.getParentUuid()))
                : null;

        DigitalObject digitalObject = DigitalObject.builder()
                .pid(metadata.getPid())
                .model(metadata.getModel())
                .title(metadata.getTitle())
                .level(metadata.getLevel())
                .indexInParent(metadata.getIndexInParent())
                .parent(parent)
                .build();

        DigitalObject saved = digitalObjectRepository.save(digitalObject);
        refreshPageCountsForAncestors(saved.getUuid());
        return saved;
    }

    /**
     * Recomputes {@link DigitalObject#getPagesCount()} and
     * {@link DigitalObject#getPagesWithAlto()}
     * for the given node and all its ancestors, and persists the values.
     * Call after hierarchy or ALTO changes that affect descendant pages.
     */
    @Transactional
    public void refreshPageCountsForAncestors(UUID uuid) {
        entityManager.flush();

        DigitalObject current = digitalObjectRepository.findById(uuid).orElse(null);
        if (current == null) {
            return;
        }
        // Refresh the node itself (hasSubhierarchy)
        current.setHasSubhierarchy(digitalObjectRepository.existsNonPageChild(current.getUuid()));
        digitalObjectRepository.save(current);
        current = current.getParent();

        while (current != null) {
            PageCountStats stats = digitalObjectRepository.getDescendantPageStats(current.getUuid());
            int total = stats != null && stats.getTotalPages() != null ? stats.getTotalPages() : 0;
            int withAlto = stats != null && stats.getPagesWithAlto() != null ? stats.getPagesWithAlto() : 0;
            current.setPagesCount(total);
            current.setPagesWithAlto(withAlto);
            current.setHasSubhierarchy(digitalObjectRepository.existsNonPageChild(current.getUuid()));
            digitalObjectRepository.save(current);
            current = current.getParent();
        }
    }

    public Batch createFetchFromKrameriusBatch(String pid, String instance, BatchPriority priority, Long userId) {
        Batch batch = batchRepository.save(Batch.builder()
                .type(BatchType.RETRIEVE_HIERARCHY)
                .pid(pid)
                .instance(instance)
                .priority(priority)
                .createdBy(userService.getUserById(userId))
                .build());

        return batch;
    }

    public Batch createGenerateAltoBatch(String pid, String engine, String instance, BatchPriority priority, Long userId, HierarchyGenerateScope scope) {
        HierarchyGenerateInput input = HierarchyGenerateInput.builder().scope(scope != null ? scope : HierarchyGenerateScope.ALL).build();
        String data;
        try {
            data = objectMapper.writeValueAsString(input);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize hierarchy generate input", e);
        }
        Batch batch = batchRepository.save(Batch.builder()
                .type(BatchType.GENERATE_FOR_HIERARCHY)
                .pid(pid)
                .engine(engine)
                .instance(instance)
                .priority(priority)
                .data(data)
                .createdBy(userService.getUserById(userId))
                .build());

        return batch;
    }
}
