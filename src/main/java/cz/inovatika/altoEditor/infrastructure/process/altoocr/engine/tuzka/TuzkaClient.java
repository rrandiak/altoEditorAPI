package cz.inovatika.altoEditor.infrastructure.process.altoocr.engine.tuzka;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * HTTP client for one tuzka-as-a-service (taas) engine, using the user-key Jobs API
 * (`/api/v1`). Stateless per call: submit an image, then the caller polls state and
 * downloads the ALTO. The API key is applied as the {@code X-API-Key} header by the
 * WebClient this is constructed with.
 */
public class TuzkaClient {

    private final WebClient webClient;
    private final String fmt;

    public TuzkaClient(WebClient webClient, String fmt) {
        this.webClient = webClient;
        this.fmt = fmt;
    }

    /**
     * Submit a page image for OCR.
     *
     * @param externalId caller's external id for the job (must be a valid, per-user-unique UUID)
     * @param image      the page image bytes
     * @return the taas job id to poll
     */
    public String submit(String externalId, byte[] image) {
        MultipartBodyBuilder body = new MultipartBodyBuilder();
        body.part("image", new ByteArrayResource(image) {
            @Override
            public String getFilename() {
                return externalId + ".jpg";
            }
        }).contentType(MediaType.IMAGE_JPEG);
        body.part("uuid", externalId);
        body.part("fmt", fmt);

        TuzkaJobResponse response = webClient.post()
                .uri("/api/v1/jobs")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(body.build()))
                .retrieve()
                .bodyToMono(TuzkaJobResponse.class)
                .block();

        if (response == null || response.getJobId() == null) {
            throw new RuntimeException("taas did not return a job id for submission " + externalId);
        }
        return response.getJobId();
    }

    /** Download the ALTO artifact for a completed job (server-side stream, decompressed). */
    public byte[] downloadAlto(String jobId) {
        byte[] alto = webClient.get()
                .uri("/api/v1/jobs/{jobId}/result/{fmt}/download", jobId, fmt)
                .retrieve()
                .bodyToMono(byte[].class)
                .block();

        if (alto == null || alto.length == 0) {
            throw new RuntimeException("taas returned empty ALTO for job " + jobId);
        }
        return alto;
    }
}
