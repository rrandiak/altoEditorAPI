package cz.inovatika.altoEditor.infrastructure.kramerius.adapter.k7;

import java.net.URI;
import java.util.List;
import java.util.function.Function;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import cz.inovatika.altoEditor.config.properties.KrameriusProperties;
import cz.inovatika.altoEditor.domain.enums.Datastream;
import cz.inovatika.altoEditor.exception.AltoVersionNotFoundException;
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

@Slf4j
public class K7Client implements KrameriusClient {

    private static final String METADATA_FL = "pid,model,title.search,level,own_parent.pid,rels_ext_index.sort";

    private final KrameriusProperties.KrameriusInstance config;

    private final RestTemplate restTemplate;

    private final KrameriusUserFactory krameriusUserFactory;

    public K7Client(KrameriusProperties.KrameriusInstance config, RestTemplate restTemplate,
            KrameriusUserFactory krameriusUserFactory) {
        this.config = config;
        this.restTemplate = restTemplate;
        this.krameriusUserFactory = krameriusUserFactory;
    }

    /**
     * Cached service token used for service-to-service calls.
     * Volatile to ensure visibility across threads when this client
     * instance is reused from caches.
     */
    private volatile String serviceToken;

    private HttpEntity<String> createJsonRequestEntity(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        return new HttpEntity<>(headers);
    }

    private HttpEntity<Void> createXmlAcceptRequestEntity(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setAccept(List.of(MediaType.APPLICATION_XML, MediaType.TEXT_XML, MediaType.ALL));
        return new HttpEntity<>(headers);
    }

    private HttpEntity<byte[]> createXmlContentEntity(String token, byte[] body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        return new HttpEntity<>(body, headers);
    }

    private HttpEntity<String> createJsonContentEntity(String token, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    private String getServiceToken() {
        // Fast path without locking
        String token = serviceToken;
        if (token == null) {
            synchronized (this) {
                token = serviceToken;
                if (token == null) {
                    ResponseEntity<K7AccessToken> response = restTemplate.exchange(
                            config.buildEndpoint("/search/api/exts/v7.0/tokens/" + config.getServiceClientId()
                                    + "?secrets=" + config.getServiceSecret()),
                            HttpMethod.GET,
                            null,
                            K7AccessToken.class);

                    token = response.getBody().getAccessToken();
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
    private <T> ResponseEntity<T> exchangeWithServiceToken(Function<String, ResponseEntity<T>> requestSupplier) {
        String token = getServiceToken();
        try {
            return requestSupplier.apply(token);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().equals(HttpStatus.UNAUTHORIZED) || e.getStatusCode().equals(HttpStatus.FORBIDDEN)) {
                synchronized (this) {
                    serviceToken = null;
                }
                String refreshedToken = getServiceToken();
                return requestSupplier.apply(refreshedToken);
            }
            throw e;
        }
    }

    private SolrResponse<K7ObjectMetadataDoc> searchInSolr(String query, String returnFields) {
        URI uri = UriComponentsBuilder
                .fromUriString(this.config.buildEndpoint("/search/api/client/v7.0/search"))
                .queryParam("q", query)
                .queryParam("fl", returnFields)
                .build()
                .toUri();

        ParameterizedTypeReference<SolrResponse<K7ObjectMetadataDoc>> responseType = new ParameterizedTypeReference<>() {
        };

        ResponseEntity<SolrResponse<K7ObjectMetadataDoc>> response = exchangeWithServiceToken(
                token -> restTemplate.exchange(
                        uri,
                        HttpMethod.GET,
                        createJsonRequestEntity(token),
                        responseType));

        return response.getBody();
    }

    @Override
    public KrameriusUser getUser(String userToken) {
        ResponseEntity<K7UserResponse> response = restTemplate.exchange(
                config.buildEndpoint("/search/api/client/v7.0/user"),
                HttpMethod.GET,
                createJsonRequestEntity(userToken),
                K7UserResponse.class);

        K7UserResponse userResponse = response.getBody();
        if (userResponse == null) {
            throw new RuntimeException("Failed to get user info from Kramerius");
        }

        return krameriusUserFactory.from(userResponse.getUid(), userResponse.getRoles());
    }

    @Override
    public boolean hasPermissionToRead(String pid, String userToken) {
        ResponseEntity<Void> response = restTemplate.exchange(
                config.buildEndpoint("/search/api/client/v7.0/items/" + pid),
                HttpMethod.HEAD,
                createJsonRequestEntity(userToken),
                Void.class);

        return response.getStatusCode().is2xxSuccessful();
    }

    @Override
    public KrameriusObjectMetadata getObjectMetadata(String pid) {
        SolrResponse<K7ObjectMetadataDoc> solrResponse = searchInSolr("pid:\"" + pid + "\"", METADATA_FL);

        if (solrResponse == null || solrResponse.getResponse().getDocs().isEmpty()) {
            throw new RuntimeException("Object with PID " + pid + " not found in Kramerius");
        }

        return solrResponse.getResponse().getDocs().get(0).toMetadata();
    }

    @Override
    public List<KrameriusObjectMetadata> getChildrenMetadata(String pid) {
        SolrResponse<K7ObjectMetadataDoc> solrResponse = searchInSolr("own_parent.pid:\"" + pid + "\"", METADATA_FL);

        if (solrResponse == null || solrResponse.getResponse().getDocs().isEmpty()) {
            throw new RuntimeException("Object with PID " + pid + " not found in Kramerius");
        }

        return solrResponse.getResponse().getDocs().stream().map(K7ObjectMetadataDoc::toMetadata).toList();
    }

    @Override
    public int getPagesCount(String pid) {
        SolrResponse<K7ObjectMetadataDoc> solrResponse = searchInSolr("pid:\"" + pid + "\"", "count_page");

        if (solrResponse == null || solrResponse.getResponse().getDocs().isEmpty()) {
            throw new RuntimeException("Object with PID " + pid + " not found in Kramerius");
        }

        return solrResponse.getResponse().getDocs().get(0).getPagesCount();
    }

    @Override
    public int getChildrenCount(String pid) {
        SolrResponse<K7ObjectMetadataDoc> solrResponse = searchInSolr(
                "own_parent.pid:\"" + pid + "\"",
                "pid");

        if (solrResponse == null) {
            throw new RuntimeException("Object with PID " + pid + " not found in Kramerius");
        }

        return solrResponse.getResponse().getDocs().size();
    }

    @Override
    public byte[] getImageBytes(String pid) {
        ResponseEntity<byte[]> response = exchangeWithServiceToken(
                token -> restTemplate.exchange(
                        this.config.buildEndpoint("/search/api/client/v7.0/items/" + pid + "/image"),
                        HttpMethod.GET,
                        createJsonRequestEntity(token),
                        byte[].class));

        return response.getBody();
    }

    @Override
    public byte[] getAltoBytes(String pid) {
        ResponseEntity<byte[]> response = exchangeWithServiceToken(
                token -> restTemplate.exchange(
                        this.config.buildEndpoint("/search/api/client/v7.0/items/" + pid + "/ocr/alto"),
                        HttpMethod.GET,
                        createXmlAcceptRequestEntity(token),
                        byte[].class));
        
        if (response.getStatusCode() == HttpStatus.NOT_FOUND) {
            throw new AltoVersionNotFoundException("Alto version for PID " + pid + " not found in Kramerius " + config.getTitle());
        } else if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Failed to get alto bytes for PID " + pid + " from Kramerius " + config.getTitle());
        }

        return response.getBody();    }

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
        URI uri = UriComponentsBuilder
                .fromUriString(config.buildEndpoint("/search/api/admin/v7.0/repository/deleteDatastream"))
                .queryParam("dsId", ds)
                .queryParam("pid", pid)
                .build()
                .encode()
                .toUri();

        try {
            ResponseEntity<K7AkubraOpResponse> response = exchangeWithServiceToken(
                    token -> restTemplate.exchange(
                            uri,
                            HttpMethod.DELETE,
                            createJsonRequestEntity(token),
                            K7AkubraOpResponse.class));

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("Failed to delete datastream " + ds + " for PID " + pid);
            }

            if (response.getBody() == null || response.getBody().getDsId() != ds) {
                throw new RuntimeException("Failed to delete datastream " + ds + " for PID " + pid);
            }
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                log.info("Datastream {} for PID {} was already absent in Kramerius {}", ds, pid, config.getTitle());
                return;
            }
            throw e;
        }
    }

    private void uploadDatastream(String pid, Datastream ds, byte[] content) {
        URI uri = UriComponentsBuilder
                .fromUriString(config.buildEndpoint("/search/api/admin/v7.0/repository/createManagedDatastream"))
                .queryParam("mimeType", ds.getMimeType())
                .queryParam("dsId", ds)
                .queryParam("pid", pid)
                .build()
                .encode()
                .toUri();

        ResponseEntity<K7AkubraOpResponse> response = exchangeWithServiceToken(
                token -> restTemplate.exchange(
                        uri,
                        HttpMethod.POST,
                        createXmlContentEntity(token, content),
                        K7AkubraOpResponse.class));

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Failed to upload datastream " + ds + " for PID " + pid);
        }

        if (response.getBody() == null || response.getBody().getDsId() != ds) {
            throw new RuntimeException("Failed to upload datastream " + ds + " for PID " + pid);
        }
    }

    private UploadAltoOcrResponse planIndexationProcess(String pid) {
        URI uri = UriComponentsBuilder
                .fromUriString(config.buildEndpoint("/search/api/admin/v7.0/processes"))
                .build()
                .encode()
                .toUri();
        K7ReindexProcess processDef = new K7ReindexProcess(pid);

        ResponseEntity<K7PlanProcessResponse> response = exchangeWithServiceToken(
                token -> restTemplate.exchange(
                        uri,
                        HttpMethod.POST,
                        createJsonContentEntity(token, processDef.toJson()),
                        K7PlanProcessResponse.class));

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Failed to plan indexation for PID " + pid);
        }

        String uuid = response.getBody().getUuid();

        return new UploadAltoOcrResponse(uuid, getProcessLink(uuid));
    }

    private String getProcessLink(String processUuid) {
        ResponseEntity<K7ProcessBatch> response = exchangeWithServiceToken(
                token -> restTemplate.exchange(
                        config.buildEndpoint("/search/api/admin/v7.0/processes/by_process_uuid/" + processUuid),
                        HttpMethod.GET,
                        createJsonRequestEntity(token),
                        K7ProcessBatch.class));

        String processId = response.getBody().getProcess().getId();

        String adminUrl = config.getAdminUrl();

        if (adminUrl != null && !adminUrl.isBlank()) {
            return adminUrl.replaceAll("/+$", "") + "/processes/standard-output/" + processId;
        }

        return config.buildEndpoint("/search/api/admin/v7.0/processes/by_process_id/" + processId);
    }
}
