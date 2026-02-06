package cz.inovatika.altoEditor.domain.model;

import org.hibernate.search.mapper.pojo.bridge.RoutingBridge;
import org.hibernate.search.mapper.pojo.bridge.binding.RoutingBindingContext;
import org.hibernate.search.mapper.pojo.bridge.mapping.programmatic.RoutingBinder;
import org.hibernate.search.mapper.pojo.bridge.runtime.RoutingBridgeRouteContext;
import org.hibernate.search.mapper.pojo.route.DocumentRoutes;

import cz.inovatika.altoEditor.domain.enums.Model;

/**
 * Conditional indexing for {@link DigitalObject}: only hierarchy nodes (monographs, periods, etc.)
 * are indexed; pages (model = "page") are excluded from the search index.
 *
 * @see <a href="https://gauthier-cassany.com/posts/spring-boot-hibernate-search-conditional-indexing">Conditional Indexing in Hibernate Search</a>
 */
public class DigitalObjectRoutingBinder implements RoutingBinder {

    private static final String MODEL_FIELD = "model";

    @Override
    public void bind(RoutingBindingContext context) {
        context.dependencies().use(MODEL_FIELD);
        context.bridge(DigitalObject.class, new Bridge());
    }

    public static class Bridge implements RoutingBridge<DigitalObject> {

        @Override
        public void route(DocumentRoutes routes, Object entityIdentifier, DigitalObject entity,
                RoutingBridgeRouteContext context) {
            if (entity != null && Model.PAGE.getModelName().equals(entity.getModel())) {
                routes.notIndexed();
            } else {
                routes.addRoute();
            }
        }

        @Override
        public void previousRoutes(DocumentRoutes routes, Object entityIdentifier, DigitalObject entity,
                RoutingBridgeRouteContext context) {
            routes.addRoute();
        }
    }
}
