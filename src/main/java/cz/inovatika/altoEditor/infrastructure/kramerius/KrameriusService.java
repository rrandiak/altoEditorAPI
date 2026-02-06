package cz.inovatika.altoEditor.infrastructure.kramerius;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import cz.inovatika.altoEditor.config.properties.KrameriusProperties;
import cz.inovatika.altoEditor.infrastructure.kramerius.model.KrameriusObjectMetadata;
import cz.inovatika.altoEditor.infrastructure.kramerius.model.KrameriusUser;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class KrameriusService {

    private final KrameriusClientFactory clientFactory;

    private final KrameriusProperties krameriusConfig;

    private final ConcurrentHashMap<String, KrameriusClient> clientCache = new ConcurrentHashMap<>();

    private KrameriusClient getClient(String instance) {
        if (instance == null) {
            throw new IllegalArgumentException("Instance must be provided");
        }
        return clientCache.computeIfAbsent(instance, clientFactory::getClient);
    }

    public KrameriusUser getUser(String token) {
        for (String instance : krameriusConfig.getKrameriusInstances().keySet()) {
            KrameriusUser user = getClient(instance).getUser(token);

            if (user != null) {
                return user;
            }
        }

        return null;
    }

    public boolean hasPermissionToRead(String pid, String instance, String userToken) {
        return getClient(instance).hasPermissionToRead(pid, userToken);
    }

    public KrameriusObjectMetadata getObjectMetadata(String pid, String instance) {
        return getClient(instance).getObjectMetadata(pid);
    }

    public int getChildrenCount(String pid, String instance) {
        return getClient(instance).getChildrenCount(pid);
    }

    public int getPagesCount(String pid, String instance) {
        return getClient(instance).getPagesCount(pid);
    }

    public List<KrameriusObjectMetadata> getChildrenMetadata(String pid, String instance) {
        return getClient(instance).getChildrenMetadata(pid);
    }

    public byte[] getAltoBytes(String pid, String instance) {
        return getClient(instance).getAltoBytes(pid);
    }

    public byte[] getImageBytes(String pid, String instance) {
        return getClient(instance).getImageBytes(pid);
    }

    public void uploadAltoOcr(String pid, byte[] alto, byte[] ocr) {
        for (String instance : krameriusConfig.getKrameriusInstances().keySet()) {
            getClient(instance).uploadAltoOcr(pid, alto, ocr);
        }
    }
}