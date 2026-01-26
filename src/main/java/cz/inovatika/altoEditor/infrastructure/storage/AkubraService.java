package cz.inovatika.altoEditor.infrastructure.storage;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.ceskaexpedice.fedoramodel.DatastreamType;
import org.ceskaexpedice.fedoramodel.DatastreamVersionType;
import org.ceskaexpedice.fedoramodel.DigitalObject;
import org.fcrepo.server.errors.LowlevelStorageException;
import org.fcrepo.server.storage.lowlevel.akubra.AkubraLowlevelStorage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.akubraproject.BlobStore;
import org.akubraproject.fs.FSBlobStore;
import org.akubraproject.map.IdMapper;
import org.akubraproject.map.IdMappingBlobStore;
import org.fcrepo.server.storage.lowlevel.akubra.HashPathIdMapper;
import java.net.URI;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Unmarshaller;

import cz.inovatika.altoEditor.config.properties.StoreProperties;
import cz.inovatika.altoEditor.domain.enums.Datastream;

@Service
public class AkubraService {

    private final AkubraLowlevelStorage storage;

    private final BlockingQueue<Unmarshaller> unmarshallerPool;

    @Autowired
    public AkubraService(StoreProperties config) {
        BlobStore dsStore;
        try {
            BlobStore fsDsStore = new FSBlobStore(new URI("urn:example.org:fsDatastreamStore"),
                    new File(config.getPath()));
            IdMapper fsDsStoreMapper = new HashPathIdMapper(config.getNormalizedPattern());
            dsStore = new IdMappingBlobStore(new URI("urn:example.org:datastreamStore"), fsDsStore,
                    fsDsStoreMapper);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        this.storage = new AkubraLowlevelStorage(null, dsStore, false, true);

        this.unmarshallerPool = new LinkedBlockingQueue<>(config.getUnmarshallerPoolSize());

        try {
            JAXBContext jaxbContext = JAXBContext.newInstance(DigitalObject.class);
            for (int i = 0; i < 10; i++) {
                unmarshallerPool.offer(jaxbContext.createUnmarshaller());
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private DigitalObject unmarshallObject(InputStream foxml) {
        Unmarshaller unmarshaller = null;

        try {
            unmarshaller = unmarshallerPool.take();

            return (DigitalObject) unmarshaller.unmarshal(foxml);

        } catch (Exception e) {
            throw new RuntimeException(e);

        } finally {
            if (unmarshaller != null) {
                unmarshallerPool.offer(unmarshaller);
            }
        }
    }

    public byte[] getLatestDsVersionBinaryContent(byte[] foxml, Datastream ds) {
        DigitalObject digitalObject = unmarshallObject(new ByteArrayInputStream(foxml));

        for (DatastreamType datastreamType : digitalObject.getDatastream()) {

            if (datastreamType.getID().equals(ds.name())) {
                List<DatastreamVersionType> dvs = datastreamType.getDatastreamVersion();

                int lastVersionIndex = dvs.size() - 1;
                byte[] binaryContent = dvs.get(lastVersionIndex).getBinaryContent();

                return binaryContent;
            }
        }

        return null;
    }

    private String getDsKey(String pid, Datastream ds, int version) {
        return pid + "+" + ds.name() + "+" + ds.name() + "." + version;
    }

    private void saveDatastreamContent(String pid, Datastream ds, int version, byte[] binaryContent) {
        String dsKey = getDsKey(pid, ds, version);

        if (this.storage.datastreamExists(dsKey)) {
            try {
                this.storage.replaceDatastream(dsKey, new ByteArrayInputStream(binaryContent));
            } catch (LowlevelStorageException e) {
                throw new RuntimeException(e);
            }
            return;
        }

        try {
            this.storage.addDatastream(dsKey, new ByteArrayInputStream(binaryContent));
        } catch (LowlevelStorageException e) {
            throw new RuntimeException(e);
        }
    }

    public void saveAltoContent(String pid, int version, byte[] bytes) {
        // TODO: Check if its ALTO datastream
        saveDatastreamContent(pid, Datastream.ALTO, version, bytes);
    }

    public void saveOcrContent(String pid, int version, byte[] bytes) {
        saveDatastreamContent(pid, Datastream.TEXT_OCR, version, bytes);
    }

    public byte[] retrieveDsBinaryContent(String pid, Datastream ds, int version) {
        String dsKey = getDsKey(pid, ds, version);

        if (!this.storage.datastreamExists(dsKey)) {
            throw new RuntimeException("Datastream does not exist: " + dsKey);
        }

        try (InputStream is = this.storage.retrieveDatastream(dsKey);
                ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {

            byte[] data = new byte[4096];
            int nRead;

            while ((nRead = is.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, nRead);
            }

            return buffer.toByteArray();
            // byte[] base64Bytes = buffer.toByteArray();

            // return Base64.getDecoder().decode(base64Bytes);
        } catch (LowlevelStorageException | IOException e) {
            throw new RuntimeException(e);
        }
    }
}
