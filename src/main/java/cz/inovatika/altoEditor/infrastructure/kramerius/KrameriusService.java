package cz.inovatika.altoEditor.infrastructure.kramerius;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import cz.inovatika.altoEditor.config.properties.KrameriusProperties;
import cz.inovatika.altoEditor.config.properties.KrameriusProperties.KrameriusInstance;
import cz.inovatika.altoEditor.infrastructure.kramerius.model.KrameriusObjectMetadata;
import cz.inovatika.altoEditor.infrastructure.kramerius.model.KrameriusProcessState;
import cz.inovatika.altoEditor.infrastructure.kramerius.model.KrameriusUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
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
                user.setInstance(instance);
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

    public void planObjectIndexing(String pid) {
        for (String instance : krameriusConfig.getKrameriusInstances().keySet()) {
            getClient(instance).planObjectIndexing(pid);
        }
    }

    public void planObjectIndexing(List<String> pids) {
        for (String instance : krameriusConfig.getKrameriusInstances().keySet()) {
            getClient(instance).planObjectIndexing(pids);
        }
    }

    public void planHierarchyIndexing(String pid) {
        for (String instance : krameriusConfig.getKrameriusInstances().keySet()) {
            getClient(instance).planHierarchyIndexing(pid);
        }
    }

    /**
     * Make accepted ALTO searchable: rebuild the processing index, wait for it to finish,
     * then reindex. Per instance the steps are ordered — the reindex only runs once the
     * rebuild has completed. Throws if the rebuild fails or times out, so the caller can
     * fail the batch.
     *
     * <p>Rebuild is <b>not</b> recursive, so it targets every accepted page pid (one
     * process, {@code ;}-joined target). Reindex <b>is</b> recursive, so when the pid the
     * batch was accepted with is known it reindexes that pid's subtree (covering its
     * pages, and only that item — not any broader ancestor tree); otherwise it reindexes
     * the accepted page pids directly.
     *
     * @param pagePids    the accepted page pids to rebuild (and reindex when no accepted pid)
     * @param acceptedPid the pid the batch was accepted with, to reindex recursively, or {@code null}
     */
    public void rebuildAndReindex(List<String> pagePids, String acceptedPid) {
        if (pagePids == null || pagePids.isEmpty()) {
            return;
        }
        String rebuildTarget = String.join(";", pagePids);
        for (String instance : krameriusConfig.getKrameriusInstances().keySet()) {
            KrameriusClient client = getClient(instance);
            String processUuid = client.planRebuildProcessingIndex(rebuildTarget);
            awaitProcess(instance, client, processUuid);
            if (acceptedPid != null) {
                client.planHierarchyIndexing(acceptedPid);
            } else {
                client.planObjectIndexing(pagePids);
            }
        }
    }

    private void awaitProcess(String instance, KrameriusClient client, String processUuid) {
        KrameriusInstance cfg = krameriusConfig.getKrameriusInstances().get(instance);
        long intervalMillis = cfg.getProcessPollIntervalMillis();
        long deadline = System.currentTimeMillis() + cfg.getProcessPollTimeoutMillis();

        while (true) {
            KrameriusProcessState state = client.getProcessState(processUuid);
            if (state == KrameriusProcessState.FINISHED) {
                return;
            }
            if (state == KrameriusProcessState.FAILED) {
                throw new RuntimeException(
                        "Kramerius process " + processUuid + " failed on instance " + instance);
            }
            if (System.currentTimeMillis() >= deadline) {
                throw new RuntimeException(
                        "Timed out waiting for Kramerius process " + processUuid + " on instance " + instance);
            }
            sleep(intervalMillis);
        }
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for a Kramerius process", e);
        }
    }
}