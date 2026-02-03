package cz.inovatika.altoEditor.infrastructure.kramerius.adapter.k7;

import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import cz.inovatika.altoEditor.config.properties.AuthProperties;
import cz.inovatika.altoEditor.infrastructure.kramerius.KrameriusAuthClient;
import cz.inovatika.altoEditor.infrastructure.kramerius.adapter.k7.model.K7UserResponse;
import cz.inovatika.altoEditor.infrastructure.kramerius.model.KrameriusUser;
import cz.inovatika.altoEditor.infrastructure.kramerius.model.KrameriusUserFactory;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class K7AuthClient extends K7AbstractClient implements KrameriusAuthClient {

    private final AuthProperties config;

    private final RestTemplate restTemplate;

    private final KrameriusUserFactory krameriusUserFactory;

    @Override
    public KrameriusUser getUser(String token) {
        ResponseEntity<K7UserResponse> response = restTemplate.exchange(
                config.getUserInfoUrl(),
                HttpMethod.GET,
                createJsonRequestEntity(token),
                K7UserResponse.class);

        K7UserResponse userResponse = response.getBody();
        if (userResponse == null) {
            throw new RuntimeException("Failed to get user info from Kramerius");
        }

        return krameriusUserFactory.from(userResponse.getUid(), userResponse.getRoles());
    }

    @Override
    public String getServiceToken() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getServiceToken'");
    }
}
