package cz.inovatika.altoEditor.domain.service;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.hibernate.search.engine.search.query.SearchResult;
import org.hibernate.search.engine.search.sort.dsl.SortOrder;
import org.hibernate.search.mapper.orm.Search;
import org.hibernate.search.mapper.orm.session.SearchSession;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import cz.inovatika.altoEditor.domain.adapter.PidAdapter;
import cz.inovatika.altoEditor.domain.enums.BatchPriority;
import cz.inovatika.altoEditor.domain.enums.BatchType;
import cz.inovatika.altoEditor.domain.enums.Model;
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

    private final TransactionTemplate transactionTemplate;

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

    @Transactional
    public List<UUID> findDescendantNodeUuidsBottomUp(String ancestorPid) {
        return digitalObjectRepository.findDescendantUuidsOrderByLevelDesc(PidAdapter.toUuid(ancestorPid));
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
    public DigitalObject fetchAndStore(String pid, String instance) {
        return fetchAndStore(pid, instance, true);
    }

    public DigitalObject fetchAndStore(String pid, String instance, boolean refreshStats) {
        // Check if already present
        Optional<DigitalObject> existing = digitalObjectRepository.findById(PidAdapter.toUuid(pid));
        if (existing.isPresent()) {
            return existing.get();
        }

        KrameriusObjectMetadata metadata = krameriusService.getObjectMetadata(pid, instance);
        if (metadata == null) {
            throw new DigitalObjectNotFoundException(PidAdapter.toUuid(pid));
        }

        DigitalObject parent = null;
        if (metadata.getParentPid() != null) {
            parent = digitalObjectRepository.findById(metadata.getParentUuid()).orElse(null);
            if (parent == null) {
                parent = fetchAndStore(metadata.getParentPid(), instance, false);
            }
        }

        final DigitalObject resolvedParent = parent;
        return transactionTemplate.execute(status -> saveResolvedNode(metadata, resolvedParent, refreshStats));
    }

    private DigitalObject saveResolvedNode(KrameriusObjectMetadata metadata, DigitalObject parent, boolean refreshStats) {
        DigitalObject saved = saveOrGetExisting(DigitalObject.builder()
                .pid(metadata.getPid())
                .model(metadata.getModel())
                .title(metadata.getTitle())
                .level(metadata.getLevel())
                .indexInParent(metadata.getIndexInParent())
                .parent(parent)
                .build());

        if (refreshStats) {
            refreshPageCountsForAncestors(saved.getUuid());
        }

        return saved;
    }

    @Transactional
    public DigitalObject store(KrameriusObjectMetadata metadata) {
        return store(metadata, true);
    }

    @Transactional
    public DigitalObject store(KrameriusObjectMetadata metadata, boolean refreshStats) {
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

        DigitalObject saved = saveOrGetExisting(digitalObject);
        if (refreshStats) {
            refreshPageCountsForAncestors(saved.getUuid());
        }
        return saved;
    }

    private DigitalObject saveOrGetExisting(DigitalObject digitalObject) {
        digitalObjectRepository.insertIfAbsent(
                digitalObject.getUuid(),
                digitalObject.getParent() != null ? digitalObject.getParent().getUuid() : null,
                digitalObject.getModel(),
                digitalObject.getTitle(),
                digitalObject.getLevel(),
                digitalObject.getIndexInParent(),
                digitalObject.getPagesCount(),
                digitalObject.getPagesWithAlto(),
                digitalObject.isHasSubhierarchy());
        return digitalObjectRepository.findById(digitalObject.getUuid())
                .orElseThrow(() -> new DigitalObjectNotFoundException(digitalObject.getUuid()));
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

    /**
     * Recomputes page-count stats for every distinct ancestor of the given accepted
     * pages, once each.
     *
     * <p>Walks each page's parent chain to the root, collecting a de-duplicated set
     * of ancestor nodes, then recomputes {@code pagesCount}/{@code pagesWithAlto}/
     * {@code hasSubhierarchy} exactly once per distinct ancestor. The counts are
     * absolute (derived from descendants via {@link DigitalObjectRepository#getDescendantPageStats}),
     * so node visit order is irrelevant and each node needs recomputing only once.
     *
     * <p>Intended to run single-threaded after a parallel accept phase: it avoids the
     * shared-row contention of per-page ancestor walks and is O(distinct ancestors)
     * instead of O(pages × depth).
     */
    @Transactional
    public void refreshPageCountsForAcceptedPages(Collection<UUID> pageUuids) {
        entityManager.flush();

        // Collect distinct ancestors. Every walk runs to the root, so once we hit an
        // already-seen ancestor, all of its ancestors are seen too and we can stop.
        Set<UUID> ancestors = new LinkedHashSet<>();
        for (UUID pageUuid : pageUuids) {
            DigitalObject current = digitalObjectRepository.findById(pageUuid).orElse(null);
            if (current == null) {
                continue;
            }
            current = current.getParent();
            while (current != null && ancestors.add(current.getUuid())) {
                current = current.getParent();
            }
        }

        for (UUID ancestorUuid : ancestors) {
            DigitalObject node = digitalObjectRepository.findById(ancestorUuid).orElse(null);
            if (node == null) {
                continue;
            }
            PageCountStats stats = digitalObjectRepository.getDescendantPageStats(node.getUuid());
            int total = stats != null && stats.getTotalPages() != null ? stats.getTotalPages() : 0;
            int withAlto = stats != null && stats.getPagesWithAlto() != null ? stats.getPagesWithAlto() : 0;
            node.setPagesCount(total);
            node.setPagesWithAlto(withAlto);
            node.setHasSubhierarchy(digitalObjectRepository.existsNonPageChild(node.getUuid()));
            digitalObjectRepository.save(node);
        }
    }

    /**
     * Recomputes pagesCount/pagesWithAlto/hasSubhierarchy for the given target
     * using only its direct children. Non-page children are expected to already
     * have correct persisted counts.
     */
    @Transactional
    public void refreshPageCountsForTarget(UUID uuid) {
        DigitalObject target = digitalObjectRepository.findById(uuid).orElse(null);
        if (target == null) {
            return;
        }

        List<DigitalObject> children = digitalObjectRepository.findByParentUuid(uuid);
        HashSet<UUID> pageChildrenWithAlto = new HashSet<>(
                digitalObjectRepository.findDirectChildPageUuidsWithAlto(uuid));

        int totalPages = 0;
        int pagesWithAlto = 0;
        boolean hasSubhierarchy = false;

        for (DigitalObject child : children) {
            if (Model.PAGE.isModel(child.getModel())) {
                totalPages++;
                if (pageChildrenWithAlto.contains(child.getUuid())) {
                    pagesWithAlto++;
                }
                continue;
            }

            hasSubhierarchy = true;
            totalPages += child.getPagesCount() != null ? child.getPagesCount() : 0;
            pagesWithAlto += child.getPagesWithAlto() != null ? child.getPagesWithAlto() : 0;
        }

        target.setPagesCount(totalPages);
        target.setPagesWithAlto(pagesWithAlto);
        target.setHasSubhierarchy(hasSubhierarchy);
        digitalObjectRepository.save(target);
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

    public Batch createGenerateAltoBatch(String pid, String engine, String instance, BatchPriority priority,
            Long userId, HierarchyGenerateScope scope) {
        HierarchyGenerateInput input = HierarchyGenerateInput.builder()
                .scope(scope != null ? scope : HierarchyGenerateScope.ALL).build();
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
