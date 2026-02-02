package cz.inovatika.altoEditor.domain.service;

import org.hibernate.search.engine.search.query.SearchResult;
import org.hibernate.search.mapper.orm.Search;
import org.hibernate.search.mapper.orm.session.SearchSession;
import org.springframework.stereotype.Service;

import cz.inovatika.altoEditor.domain.model.DigitalObject;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ObjectHierarchyService {

    private final EntityManager entityManager;

    public SearchResult<DigitalObject> search(String pid, String parentPid, String model, String title, Integer level, int offset, int limit) {
        SearchSession session = Search.session(entityManager);

        var searchQuery = session.search(DigitalObject.class)
            .where(f -> {
                var bool = f.bool();
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
                return bool;
            });
        
        return searchQuery.fetch(offset, limit);
    }
}
