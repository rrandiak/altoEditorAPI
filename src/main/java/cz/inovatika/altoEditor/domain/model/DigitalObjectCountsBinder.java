package cz.inovatika.altoEditor.domain.model;

import org.hibernate.search.engine.backend.document.IndexFieldReference;
import org.hibernate.search.engine.backend.document.DocumentElement;
import org.hibernate.search.mapper.pojo.bridge.TypeBridge;
import org.hibernate.search.mapper.pojo.bridge.binding.TypeBindingContext;
import org.hibernate.search.mapper.pojo.bridge.mapping.programmatic.TypeBinder;
import org.hibernate.search.mapper.pojo.bridge.runtime.TypeBridgeWriteContext;
import org.springframework.stereotype.Component;

import cz.inovatika.altoEditor.domain.model.dto.PageCountStats;
import cz.inovatika.altoEditor.domain.repository.DigitalObjectRepository;
import lombok.RequiredArgsConstructor;

/**
 * Binds index fields {@code pagesCount} and {@code pagesWithAlto} on {@link DigitalObject}.
 * Values are computed at index time via {@link DigitalObjectRepository#getDescendantPageStats(java.util.UUID)}
 */
@Component
@RequiredArgsConstructor
public class DigitalObjectCountsBinder implements TypeBinder {

    private static final String PAGES_COUNT = "pagesCount";
    private static final String PAGES_WITH_ALTO = "pagesWithAlto";

    private final DigitalObjectRepository digitalObjectRepository;

    @Override
    public void bind(TypeBindingContext context) {
        context.dependencies().use("uuid");

        IndexFieldReference<Integer> pagesCountRef = context.indexSchemaElement()
                .field(PAGES_COUNT, context.typeFactory().asInteger())
                .toReference();
        IndexFieldReference<Integer> pagesWithAltoRef = context.indexSchemaElement()
                .field(PAGES_WITH_ALTO, context.typeFactory().asInteger())
                .toReference();

        context.bridge(DigitalObject.class, new Bridge(digitalObjectRepository, pagesCountRef, pagesWithAltoRef));
    }

    public static final class Bridge implements TypeBridge<DigitalObject> {

        private final DigitalObjectRepository repository;
        private final IndexFieldReference<Integer> pagesCountRef;
        private final IndexFieldReference<Integer> pagesWithAltoRef;

        public Bridge(DigitalObjectRepository repository,
                IndexFieldReference<Integer> pagesCountRef,
                IndexFieldReference<Integer> pagesWithAltoRef) {
            this.repository = repository;
            this.pagesCountRef = pagesCountRef;
            this.pagesWithAltoRef = pagesWithAltoRef;
        }

        @Override
        public void write(DocumentElement target, DigitalObject bridgedElement, TypeBridgeWriteContext context) {
            PageCountStats stats = repository.getDescendantPageStats(bridgedElement.getUuid());
            int total = stats != null && stats.getTotalPages() != null ? stats.getTotalPages() : 0;
            int withAlto = stats != null && stats.getPagesWithAlto() != null ? stats.getPagesWithAlto() : 0;
            target.addValue(pagesCountRef, total);
            target.addValue(pagesWithAltoRef, withAlto);
        }
    }
}
