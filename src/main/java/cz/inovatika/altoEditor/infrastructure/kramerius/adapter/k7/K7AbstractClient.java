package cz.inovatika.altoEditor.infrastructure.kramerius.adapter.k7;

import java.util.List;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

public class K7AbstractClient {

    protected HttpEntity<String> createJsonRequestEntity(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        return new HttpEntity<>(headers);
    }

    protected HttpEntity<byte[]> createXmlContentEntity(String token, byte[] body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        return new HttpEntity<>(body, headers);
    }

    protected HttpEntity<String> createJsonContentEntity(String token, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }
}
