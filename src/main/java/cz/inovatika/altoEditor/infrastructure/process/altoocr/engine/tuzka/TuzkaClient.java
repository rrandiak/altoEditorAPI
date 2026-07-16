package cz.inovatika.altoEditor.infrastructure.process.altoocr.engine.tuzka;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.github.luben.zstd.ZstdInputStream;

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

        TuzkaJobResponse response;
        try {
            response = webClient.post()
                    .uri("/api/v1/jobs")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(body.build()))
                    .retrieve()
                    .bodyToMono(TuzkaJobResponse.class)
                    .block();
        } catch (WebClientResponseException e) {
            // Surface the taas response body — a bare 5xx/4xx from POST /jobs hides the reason
            // (e.g. a duplicate external id, or a rejected image).
            throw new RuntimeException("taas POST /jobs failed for " + externalId + ": "
                    + e.getStatusCode() + " " + e.getResponseBodyAsString(), e);
        }

        if (response == null || response.getJobId() == null) {
            throw new RuntimeException("taas did not return a job id for submission " + externalId);
        }
        return response.getJobId();
    }

    /**
     * Download the ALTO artifact for a completed job. taas stores results zstd-compressed and
     * the download endpoint streams the stored object as-is, so decompress when the bytes carry
     * the zstd magic number (and pass through if a deployment ever returns raw XML).
     */
    public byte[] downloadAlto(String jobId) {
        byte[] body = webClient.get()
                .uri("/api/v1/jobs/{jobId}/result/{fmt}/download", jobId, fmt)
                .retrieve()
                .bodyToMono(byte[].class)
                .block();

        if (body == null || body.length == 0) {
            throw new RuntimeException("taas returned empty ALTO for job " + jobId);
        }
        return decompressIfZstd(body);
    }

    static boolean isZstd(byte[] content) {
        return content.length >= 4
                && (content[0] & 0xFF) == 0x28
                && (content[1] & 0xFF) == 0xB5
                && (content[2] & 0xFF) == 0x2F
                && (content[3] & 0xFF) == 0xFD;
    }

    static byte[] decompressIfZstd(byte[] content) {
        if (!isZstd(content)) {
            return content;
        }
        try (ByteArrayInputStream input = new ByteArrayInputStream(content);
                ZstdInputStream zstd = new ZstdInputStream(input);
                ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            byte[] chunk = new byte[8192];
            int n;
            while ((n = zstd.read(chunk, 0, chunk.length)) != -1) {
                buffer.write(chunk, 0, n);
            }
            return buffer.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to zstd-decompress taas result", e);
        }
    }
}
