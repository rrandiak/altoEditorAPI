package cz.inovatika.altoEditor.infrastructure.kramerius.adapter.k7;

import java.net.URI;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import cz.inovatika.altoEditor.config.properties.KrameriusProperties;
import cz.inovatika.altoEditor.domain.enums.Datastream;
import cz.inovatika.altoEditor.infrastructure.kramerius.KrameriusClient;
import cz.inovatika.altoEditor.infrastructure.kramerius.adapter.k7.model.K7AkubraOpResponse;
import cz.inovatika.altoEditor.infrastructure.kramerius.adapter.k7.model.K7ObjectMetadataDoc;
import cz.inovatika.altoEditor.infrastructure.kramerius.adapter.k7.model.K7PlanProcessResponse;
import cz.inovatika.altoEditor.infrastructure.kramerius.adapter.k7.model.K7ProcessBatch;
import cz.inovatika.altoEditor.infrastructure.kramerius.adapter.k7.model.K7ReindexProcess;
import cz.inovatika.altoEditor.infrastructure.kramerius.model.KrameriusObjectMetadata;
import cz.inovatika.altoEditor.infrastructure.kramerius.model.SolrResponse;
import cz.inovatika.altoEditor.infrastructure.kramerius.model.UploadAltoOcrResponse;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class K7Client extends K7AbstractClient implements KrameriusClient {

    private final KrameriusProperties.KrameriusInstance config;

    private final RestTemplate restTemplate;

    @Override
    public KrameriusObjectMetadata getObjectMetadata(String pid, String token) {
        URI uri = UriComponentsBuilder
                .fromUriString(this.config.buildEndpoint("/search/api/client/v7.0/search"))
                .queryParam("q", "pid:\"" + pid + "\"")
                .queryParam("fl", "pid,title.search,own_pid_path,own_parent.title")
                .build()
                .toUri();

        ParameterizedTypeReference<SolrResponse<K7ObjectMetadataDoc>> responseType = new ParameterizedTypeReference<>() {
        };

        ResponseEntity<SolrResponse<K7ObjectMetadataDoc>> response = restTemplate.exchange(
                uri,
                HttpMethod.GET,
                createJsonRequestEntity(token),
                responseType);

        SolrResponse<K7ObjectMetadataDoc> solrResponse = response.getBody();
        if (solrResponse == null || solrResponse.getResponse().getDocs().isEmpty()) {
            throw new RuntimeException("Object with PID " + pid + " not found in Kramerius");
        }

        return solrResponse.getResponse().getDocs().get(0).toMetadata();
    }

    @Override
    public byte[] getImageBytes(String pid, String token) {
        ResponseEntity<byte[]> response = restTemplate.exchange(
                this.config.buildEndpoint("/search/api/v7.0/item/" + pid + "/image"),
                HttpMethod.GET,
                createJsonRequestEntity(token),
                byte[].class);

        return response.getBody();
    }

    @Override
    public byte[] getFoxmlBytes(String pid, String token) {
        ResponseEntity<byte[]> response = restTemplate.exchange(
                this.config.buildEndpoint("/search/api/v7.0/item/" + pid + "/foxml"),
                HttpMethod.GET,
                createJsonRequestEntity(token),
                byte[].class);

        return response.getBody();
    }

    @Override
    public byte[] getAltoBytes(String pid, String token) {
        ResponseEntity<byte[]> response = restTemplate.exchange(
                this.config.buildEndpoint("/search/api/v7.0/item/" + pid + "/ocr/alto"),
                HttpMethod.GET,
                createJsonRequestEntity(token),
                byte[].class);

        return response.getBody();
    }

    @Override
    public UploadAltoOcrResponse uploadAltoOcr(String pid, byte[] altoContent, byte[] ocrContent, String token) {
        replaceDatastream(pid, Datastream.ALTO, altoContent, token);

        replaceDatastream(pid, Datastream.TEXT_OCR, ocrContent, token);

        return planIndexationProcess(pid, token);
    }

    private void replaceDatastream(String pid, Datastream ds, byte[] content, String token) {
        deleteDatastream(pid, ds, token);
        uploadDatastream(pid, ds, content, token);
    }

    private void deleteDatastream(String pid, Datastream ds, String token) {
        ResponseEntity<K7AkubraOpResponse> response = restTemplate.exchange(
                this.config.buildEndpoint("/search/api/admin/v7.0/repository/deleteDatastream?dsId=$dsId&pid=$pid"
                        .replace("$dsId", ds.toString())
                        .replace("$pid", pid)),
                HttpMethod.DELETE,
                createJsonRequestEntity(token),
                K7AkubraOpResponse.class);

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Failed to delete datastream " + ds + " for PID " + pid);
        }

        if (response.getBody() == null || response.getBody().getDsId() != ds) {
            throw new RuntimeException("Failed to delete datastream " + ds + " for PID " + pid);
        }
    }

    private void uploadDatastream(String pid, Datastream ds, byte[] content, String token) {
        URI uri = UriComponentsBuilder
                .fromUriString(config.buildEndpoint("/search/api/admin/v7.0/repository/uploadDatastream"))
                .queryParam("dsId", ds)
                .queryParam("pid", pid)
                .build()
                .encode()
                .toUri();

        ResponseEntity<K7AkubraOpResponse> response = restTemplate.exchange(
                uri,
                HttpMethod.POST,
                createXmlContentEntity(token, content),
                K7AkubraOpResponse.class);

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Failed to upload datastream " + ds + " for PID " + pid);
        }

        if (response.getBody() == null || response.getBody().getDsId() != ds) {
            throw new RuntimeException("Failed to upload datastream " + ds + " for PID " + pid);
        }
    }

    private UploadAltoOcrResponse planIndexationProcess(String pid, String token) {
        URI uri = UriComponentsBuilder
                .fromUriString(config.buildEndpoint("/search/api/admin/v7.0/processes"))
                .build()
                .encode()
                .toUri();
        K7ReindexProcess processDef = new K7ReindexProcess(pid);

        ResponseEntity<K7PlanProcessResponse> response = restTemplate.exchange(
                uri,
                HttpMethod.POST,
                createJsonContentEntity(token, processDef.toJson()),
                K7PlanProcessResponse.class);

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Failed to plan indexation for PID " + pid);
        }

        String uuid = response.getBody().getUuid();

        return new UploadAltoOcrResponse(uuid, getProcessLink(uuid));
    }

    private String getProcessLink(String processUuid) {
        ResponseEntity<K7ProcessBatch> response = restTemplate.exchange(
                config.buildEndpoint("/search/api/admin/v7.0/processes/by_process_uuid/" + processUuid),
                HttpMethod.GET,
                createJsonRequestEntity(null),
                K7ProcessBatch.class);

        String processId = response.getBody().getProcess().getId();

        String adminUrl = config.getAdminUrl();

        if (adminUrl != null && !adminUrl.isBlank()) {
            return adminUrl.replaceAll("/+$", "") + "/processes/standard-output/" + processId;
        }

        return config.buildEndpoint("/search/api/admin/v7.0/processes/by_process_id/" + processId);
    }
}
