package cz.inovatika.altoEditor.infrastructure.process.altoocr.engine.tuzka;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.github.luben.zstd.ZstdOutputStream;

class TuzkaClientTest {

    private static byte[] zstd(byte[] raw) throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ZstdOutputStream zstd = new ZstdOutputStream(buffer)) {
            zstd.write(raw);
        }
        return buffer.toByteArray();
    }

    @Test
    @DisplayName("zstd-compressed ALTO download is decompressed to raw XML")
    void decompressesZstdResult() throws Exception {
        byte[] alto = "<alto xmlns=\"http://www.loc.gov/standards/alto/ns-v4#\"/>".getBytes();

        byte[] result = TuzkaClient.decompressIfZstd(zstd(alto));

        assertThat(result).isEqualTo(alto);
    }

    @Test
    @DisplayName("plain XML download passes through unchanged")
    void passesThroughRawXml() {
        byte[] alto = "<alto/>".getBytes();

        assertThat(TuzkaClient.decompressIfZstd(alto)).isEqualTo(alto);
        assertThat(TuzkaClient.isZstd(alto)).isFalse();
    }
}
