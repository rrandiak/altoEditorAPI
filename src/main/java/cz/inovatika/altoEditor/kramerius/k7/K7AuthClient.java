package cz.inovatika.altoEditor.kramerius.k7;

import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import cz.inovatika.altoEditor.config.ApplicationConfig;
import cz.inovatika.altoEditor.kramerius.KrameriusAuthClient;
import cz.inovatika.altoEditor.kramerius.domain.KrameriusUser;
import cz.inovatika.altoEditor.kramerius.domain.KrameriusUserFactory;
import cz.inovatika.altoEditor.kramerius.k7.domain.K7UserResponse;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class K7AuthClient extends K7AbstractClient implements KrameriusAuthClient {

    private final ApplicationConfig config;

    private final RestTemplate restTemplate;

    private final KrameriusUserFactory krameriusUserFactory;

    @Override
    public KrameriusUser getUser(String token) {
        ResponseEntity<K7UserResponse> response = restTemplate.exchange(
                config.getAuthKramerius().getUserInfoUrl(),
                HttpMethod.GET,
                createJsonRequestEntity(token),
                K7UserResponse.class);

        K7UserResponse userResponse = response.getBody();
        if (userResponse == null) {
            throw new RuntimeException("Failed to get user info from Kramerius");
        }

        return krameriusUserFactory.from(userResponse.getUid(), userResponse.getRoles());
    }
}
