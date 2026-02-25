package cz.inovatika.altoEditor.infrastructure.kramerius.adapter.k7;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import cz.inovatika.altoEditor.config.properties.KrameriusProperties;
import cz.inovatika.altoEditor.domain.enums.Datastream;
import cz.inovatika.altoEditor.domain.enums.Model;
import cz.inovatika.altoEditor.infrastructure.kramerius.KrameriusClient;
import cz.inovatika.altoEditor.infrastructure.kramerius.adapter.k7.model.K7AccessToken;
import cz.inovatika.altoEditor.infrastructure.kramerius.adapter.k7.model.K7AkubraOpResponse;
import cz.inovatika.altoEditor.infrastructure.kramerius.adapter.k7.model.K7ObjectMetadataDoc;
import cz.inovatika.altoEditor.infrastructure.kramerius.adapter.k7.model.K7PlanProcessResponse;
import cz.inovatika.altoEditor.infrastructure.kramerius.adapter.k7.model.K7ProcessBatch;
import cz.inovatika.altoEditor.infrastructure.kramerius.adapter.k7.model.K7ReindexProcess;
import cz.inovatika.altoEditor.infrastructure.kramerius.adapter.k7.model.K7UserResponse;
import cz.inovatika.altoEditor.infrastructure.kramerius.model.KrameriusObjectMetadata;
import cz.inovatika.altoEditor.infrastructure.kramerius.model.KrameriusUser;
import cz.inovatika.altoEditor.infrastructure.kramerius.model.KrameriusUserFactory;
import cz.inovatika.altoEditor.infrastructure.kramerius.model.SolrResponse;
import cz.inovatika.altoEditor.infrastructure.kramerius.model.UploadAltoOcrResponse;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
public class K7Client implements KrameriusClient {

    private static final String METADATA_FL = "pid,model,title.search,date.str,level,own_parent.pid,root.pid,rels_ext_index.sort,count_page";
    private static final int SOLR_CACHE_EXPIRE_MINUTES = 10;
    private static final int SOLR_CACHE_MAX_SIZE = 2000;
    private static final int CHILDREN_FETCH_ROWS = 300;

    private record SolrSearchKey(String query, String returnFields, int rows, int start, String sort) {
    }

    private final KrameriusProperties.KrameriusInstance config;
    private final WebClient webClient;
    private final KrameriusUserFactory krameriusUserFactory;

    /**
     * Cached service token used for service-to-service calls.
     * Volatile to ensure visibility across threads when this client
     * instance is reused from caches.
     */
    private volatile String serviceToken;

    private final LoadingCache<SolrSearchKey, SolrResponse<K7ObjectMetadataDoc>> solrCache = Caffeine.newBuilder()
            .expireAfterWrite(SOLR_CACHE_EXPIRE_MINUTES, TimeUnit.MINUTES)
            .maximumSize(SOLR_CACHE_MAX_SIZE)
            .build(this::fetchSearchInSolr);

    public K7Client(KrameriusProperties.KrameriusInstance config, WebClient webClient,
            KrameriusUserFactory krameriusUserFactory) {
        this.config = config;
        this.webClient = webClient;
        this.krameriusUserFactory = krameriusUserFactory;
    }

    private String getServiceToken() {
        String token = serviceToken;
        if (token == null) {
            synchronized (this) {
                token = serviceToken;
                if (token == null) {
                    K7AccessToken body = webClient.get()
                            .uri("/search/api/exts/v7.0/tokens/{clientId}?secrets={secret}",
                                    config.getServiceClientId(), config.getServiceSecret())
                            .retrieve()
                            .bodyToMono(K7AccessToken.class)
                            .block();
                    token = body != null ? body.getAccessToken() : null;
                    serviceToken = token;
                }
            }
        }
        return token;
    }

    /**
     * Execute a request that uses the service token, retrying once if the token
     * is no longer valid (401/403). On auth failure the cached token is cleared
     * and a new one is obtained.
     */
    private <T> ResponseEntity<T> exchangeWithServiceToken(
            Function<String, Mono<ResponseEntity<T>>> requestSupplier) {
        String token = getServiceToken();
        try {
            return requestSupplier.apply(token).block();
        } catch (WebClientResponseException e) {
            if (e.getStatusCode() == HttpStatus.UNAUTHORIZED || e.getStatusCode() == HttpStatus.FORBIDDEN) {
                synchronized (this) {
                    serviceToken = null;
                }
                return requestSupplier.apply(getServiceToken()).block();
            }
            throw e;
        }
    }

    private SolrResponse<K7ObjectMetadataDoc> fetchSearchInSolr(SolrSearchKey key) {
        String uri = UriComponentsBuilder
                .fromPath("/search/api/client/v7.0/search")
                .queryParam("q", key.query())
                .queryParam("fl", key.returnFields())
                .queryParam("rows", key.rows())
                .queryParam("start", key.start())
                .queryParamIfPresent("sort",
                        Optional.ofNullable(key.sort() == null || key.sort().isBlank() ? null : key.sort()))
                .build()
                .toUriString();

        ParameterizedTypeReference<SolrResponse<K7ObjectMetadataDoc>> responseType = new ParameterizedTypeReference<>() {
        };

        ResponseEntity<SolrResponse<K7ObjectMetadataDoc>> response = exchangeWithServiceToken(
                token -> webClient.get()
                        .uri(uri)
                        .headers(h -> {
                            h.setBearerAuth(token);
                            h.setAccept(List.of(MediaType.APPLICATION_JSON));
                        })
                        .retrieve()
                        .toEntity(responseType));

        if (response.getStatusCode() != HttpStatus.OK) {
            String bodyDetail = response.getBody() != null ? response.getBody().toString() : "";
            throw new RuntimeException("Failed to search in Solr: " + response.getStatusCode() + " " + bodyDetail);
        }

        if (response.getBody() == null) {
            throw new RuntimeException("Failed to search in Solr: no response body");
        }

        return response.getBody();
    }

    private SolrResponse<K7ObjectMetadataDoc> searchInSolr(String query, String returnFields, int rows, int start,
            String sort) {
        return solrCache.get(new SolrSearchKey(query, returnFields, rows, start, sort));
    }

    private SolrResponse<K7ObjectMetadataDoc> searchInSolr(String query, String returnFields, int rows) {
        return searchInSolr(query, returnFields, rows, 0, null);
    }

    @Override
    public KrameriusUser getUser(String userToken) {
        K7UserResponse userResponse = webClient.get()
                .uri("/search/api/client/v7.0/user")
                .headers(h -> {
                    h.setBearerAuth(userToken);
                    h.setAccept(List.of(MediaType.APPLICATION_JSON));
                })
                .retrieve()
                .bodyToMono(K7UserResponse.class)
                .block();

        if (userResponse == null) {
            throw new RuntimeException("Failed to get user info from Kramerius");
        }

        return krameriusUserFactory.from(userResponse.getUid(), userResponse.getRoles());
    }

    @Override
    public boolean hasPermissionToRead(String pid, String userToken) {
        try {
            webClient.head()
                    .uri("/search/api/client/v7.0/items/{pid}", pid)
                    .headers(h -> {
                        h.setBearerAuth(userToken);
                        h.setAccept(List.of(MediaType.APPLICATION_JSON));
                    })
                    .retrieve()
                    .toBodilessEntity()
                    .block();
            return true;
        } catch (WebClientResponseException e) {
            return e.getStatusCode().is2xxSuccessful();
        }
    }

    @Override
    public KrameriusObjectMetadata getObjectMetadata(String pid) {
        SolrResponse<K7ObjectMetadataDoc> solrResponse = searchInSolr(
                "pid:\"" + pid + "\" AND " + Model.getShouldIgnoreQueryPart(),
                METADATA_FL, 1);

        if (solrResponse.getResponse().getDocs().isEmpty()) {
            throw new RuntimeException("Object with PID " + pid + " not found in Kramerius");
        }

        return solrResponse.getResponse().getDocs().get(0).toMetadata();
    }

    private List<KrameriusObjectMetadata> getChildrenMetadata(String pid, String returnFields) {
        SolrResponse<K7ObjectMetadataDoc> solrResponse;
        int start = 0;
        List<KrameriusObjectMetadata> children = new ArrayList<>();

        do {
            solrResponse = searchInSolr("own_parent.pid:\"" + pid + "\" AND " + Model.getShouldIgnoreQueryPart(),
                    returnFields,
                    CHILDREN_FETCH_ROWS, start, "rels_ext_index.sort asc");

            children.addAll(
                    solrResponse.getResponse().getDocs().stream().map(K7ObjectMetadataDoc::toMetadata).toList());

            start += CHILDREN_FETCH_ROWS;
        } while (solrResponse.getResponse().getNumFound() > start);

        return children;
    }

    private List<KrameriusObjectMetadata> getCollectionChildrenMetadata(String pid) {
        SolrResponse<K7ObjectMetadataDoc> solrResponse;
        int start = 0;
        List<KrameriusObjectMetadata> children = new ArrayList<>();

        do {
            solrResponse = searchInSolr("in_collections.direct:\"" + pid + "\" AND " + Model.getShouldIgnoreQueryPart(),
                    METADATA_FL, CHILDREN_FETCH_ROWS,
                    start, "created desc");

            children.addAll(
                    solrResponse.getResponse().getDocs().stream().map(K7ObjectMetadataDoc::toMetadata).toList());

            start += CHILDREN_FETCH_ROWS;
        } while (solrResponse.getResponse().getNumFound() > start);

        return children;
    }

    @Override
    public List<KrameriusObjectMetadata> getChildrenMetadata(String pid) {
        KrameriusObjectMetadata metadata = getObjectMetadata(pid);

        if (Model.COLLECTION.isModel(metadata.getModel())) {
            return getCollectionChildrenMetadata(pid);
        }

        return getChildrenMetadata(pid, METADATA_FL);
    }

    @Override
    public int getPagesCount(String pid) {
        KrameriusObjectMetadata metadata = getObjectMetadata(pid);

        if (metadata.getPagesCount() != null) {
            return metadata.getPagesCount();
        }

        if (Model.PAGE.isModel(metadata.getModel())) {
            return 0;
        }

        if (Model.COLLECTION.isModel(metadata.getModel())) {
            SolrResponse<K7ObjectMetadataDoc> solrResponse = searchInSolr(
                    "in_collections.direct:\"" + pid + "\" AND model:page", "pid", 0);

            return solrResponse.getResponse().getNumFound();
        }

        if (metadata.getLevel() == 0) {
            SolrResponse<K7ObjectMetadataDoc> solrResponse = searchInSolr("root.pid:\"" + pid + "\" AND model:page",
                    "pid", 0);

            return solrResponse.getResponse().getNumFound();
        }

        Queue<String> pidsQueue = new LinkedList<>();
        pidsQueue.add(pid);
        AtomicInteger pagesCount = new AtomicInteger(0);

        while (!pidsQueue.isEmpty()) {
            String currPid = pidsQueue.poll();

            getChildrenMetadata(currPid, "pid,count_page").forEach(child -> {
                if (child.getPagesCount() != null) {
                    pagesCount.addAndGet(child.getPagesCount());
                } else {
                    pidsQueue.add(child.getPid());
                }
            });
        }

        return pagesCount.get();
    }

    @Override
    public int getChildrenCount(String pid) {
        KrameriusObjectMetadata metadata = getObjectMetadata(pid);

        if (Model.PAGE.isModel(metadata.getModel())) {
            return 0;
        }

        SolrResponse<K7ObjectMetadataDoc> solrResponse = searchInSolr(
                (Model.COLLECTION.isModel(metadata.getModel())
                        ? "in_collections.direct:\"" + pid + "\""
                        : "own_parent.pid:\"" + pid + "\"") + " AND " + Model.getShouldIgnoreQueryPart(),
                "pid",
                0);

        return solrResponse.getResponse().getNumFound();
    }

    @Override
    public byte[] getImageBytes(String pid) {
        ResponseEntity<byte[]> response = exchangeWithServiceToken(
                token -> webClient.get()
                        .uri("/search/api/client/v7.0/items/{pid}/image", pid)
                        .headers(h -> {
                            h.setBearerAuth(token);
                            h.setAccept(List.of(MediaType.APPLICATION_JSON));
                        })
                        .retrieve()
                        .toEntity(byte[].class));

        return response.getBody();
    }

    /**
     * Returns ALTO bytes for the given PID, or {@code null} if the ALTO datastream
     * does not exist in Kramerius (e.g. 404 or "not found in repository").
     * Does not throw when ALTO is missing.
     */
    @Override
    public byte[] getAltoBytes(String pid) {
        try {
            ResponseEntity<byte[]> response = exchangeWithServiceToken(
                    token -> webClient.get()
                            .uri("/search/api/client/v7.0/items/{pid}/ocr/alto", pid)
                            .headers(h -> {
                                h.setBearerAuth(token);
                                h.setAccept(List.of(MediaType.APPLICATION_XML, MediaType.TEXT_XML, MediaType.ALL));
                            })
                            .retrieve()
                            .toEntity(byte[].class));

            return response.getBody();
        } catch (WebClientResponseException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                String body = e.getResponseBodyAsString();
                if (body != null && body.contains("not found in repository")) {
                    return null;
                }
                throw new RuntimeException(
                        "Failed to get alto bytes for PID " + pid + " from Kramerius " + config.getTitle(), e);
            }
            throw new RuntimeException(
                    "Failed to get alto bytes for PID " + pid + " from Kramerius " + config.getTitle(), e);
        }
    }

    @Override
    public UploadAltoOcrResponse uploadAltoOcr(String pid, byte[] altoContent, byte[] ocrContent) {
        replaceDatastream(pid, Datastream.ALTO, altoContent);
        replaceDatastream(pid, Datastream.TEXT_OCR, ocrContent);
        return planIndexationProcess(pid);
    }

    private void replaceDatastream(String pid, Datastream ds, byte[] content) {
        deleteDatastream(pid, ds);
        uploadDatastream(pid, ds, content);
    }

    private void deleteDatastream(String pid, Datastream ds) {
        String uri = UriComponentsBuilder
                .fromPath("/search/api/admin/v7.0/repository/deleteDatastream")
                .queryParam("dsId", ds)
                .queryParam("pid", pid)
                .build()
                .toUriString();

        try {
            ResponseEntity<K7AkubraOpResponse> response = exchangeWithServiceToken(
                    token -> webClient.delete()
                            .uri(uri)
                            .headers(h -> {
                                h.setBearerAuth(token);
                                h.setAccept(List.of(MediaType.APPLICATION_JSON));
                            })
                            .retrieve()
                            .toEntity(K7AkubraOpResponse.class));

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("Failed to delete datastream " + ds + " for PID " + pid);
            }

            if (response.getBody() == null || response.getBody().getDsId() != ds) {
                throw new RuntimeException("Failed to delete datastream " + ds + " for PID " + pid);
            }
        } catch (WebClientResponseException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                log.info("Datastream {} for PID {} was already absent in Kramerius {}", ds, pid, config.getTitle());
                return;
            }
            throw e;
        }
    }

    private void uploadDatastream(String pid, Datastream ds, byte[] content) {
        String uri = UriComponentsBuilder
                .fromPath("/search/api/admin/v7.0/repository/createManagedDatastream")
                .queryParam("mimeType", ds.getMimeType())
                .queryParam("dsId", ds)
                .queryParam("pid", pid)
                .build()
                .toUriString();

        ResponseEntity<K7AkubraOpResponse> response = exchangeWithServiceToken(
                token -> webClient.post()
                        .uri(uri)
                        .headers(h -> {
                            h.setBearerAuth(token);
                            h.setContentType(org.springframework.http.MediaType.APPLICATION_OCTET_STREAM);
                        })
                        .bodyValue(content)
                        .retrieve()
                        .toEntity(K7AkubraOpResponse.class));

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Failed to upload datastream " + ds + " for PID " + pid);
        }

        if (response.getBody() == null || response.getBody().getDsId() != ds) {
            throw new RuntimeException("Failed to upload datastream " + ds + " for PID " + pid);
        }
    }

    private UploadAltoOcrResponse planIndexationProcess(String pid) {
        K7ReindexProcess processDef = new K7ReindexProcess(pid);

        ResponseEntity<K7PlanProcessResponse> response = exchangeWithServiceToken(
                token -> webClient.post()
                        .uri("/search/api/admin/v7.0/processes")
                        .headers(h -> {
                            h.setBearerAuth(token);
                            h.setContentType(MediaType.APPLICATION_JSON);
                        })
                        .bodyValue(processDef.toJson())
                        .retrieve()
                        .toEntity(K7PlanProcessResponse.class));

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Failed to plan indexation for PID " + pid);
        }

        String uuid = response.getBody().getUuid();
        return new UploadAltoOcrResponse(uuid, getProcessLink(uuid));
    }

    private String getProcessLink(String processUuid) {
        ResponseEntity<K7ProcessBatch> response = exchangeWithServiceToken(
                token -> webClient.get()
                        .uri("/search/api/admin/v7.0/processes/by_process_uuid/{uuid}", processUuid)
                        .headers(h -> {
                            h.setBearerAuth(token);
                            h.setAccept(List.of(MediaType.APPLICATION_JSON));
                        })
                        .retrieve()
                        .toEntity(K7ProcessBatch.class));

        String processId = response.getBody().getProcess().getId();
        String adminUrl = config.getAdminUrl();

        if (adminUrl != null && !adminUrl.isBlank()) {
            return adminUrl.replaceAll("/+$", "") + "/processes/standard-output/" + processId;
        }

        return config.buildEndpoint("/search/api/admin/v7.0/processes/by_process_id/" + processId);
    }
}
