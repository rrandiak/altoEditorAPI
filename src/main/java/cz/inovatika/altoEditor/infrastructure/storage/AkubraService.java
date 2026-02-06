package cz.inovatika.altoEditor.infrastructure.storage;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.ceskaexpedice.fedoramodel.DigitalObject;
import org.fcrepo.server.errors.LowlevelStorageException;
import org.fcrepo.server.storage.lowlevel.akubra.AkubraLowlevelStorage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.xml.sax.SAXException;
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
import cz.inovatika.altoEditor.exception.AkubraStorageException;
import cz.inovatika.altoEditor.exception.InvalidAltoXmlException;
import cz.inovatika.altoEditor.infrastructure.editor.AltoXmlService;

@Service
public class AkubraService {

    private final AkubraLowlevelStorage storage;

    @Autowired
    private final AltoXmlService altoXmlService;
    
    private final BlockingQueue<Unmarshaller> unmarshallerPool;

    @Autowired
    public AkubraService(StoreProperties config, AltoXmlService altoXmlService) {
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

        this.altoXmlService = altoXmlService;

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
        try {
            if (!altoXmlService.isAlto(bytes)) {
                throw new InvalidAltoXmlException("Provided XML is not valid ALTO.");
            }
        } catch (SAXException e) {
            throw new InvalidAltoXmlException("Provided XML is not valid ALTO.", e);
        } catch (IOException e) {
            throw new AkubraStorageException("Error during ALTO XML validation.", e);
        }

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
        } catch (LowlevelStorageException | IOException e) {
            throw new RuntimeException(e);
        }
    }
}
