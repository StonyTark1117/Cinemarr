package stonytark.cinemarr.core.platform;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;

/** Loads the credential-free H.264 High/AAC MPEG-TS fixture used by client hardware probes. */
public final class DecoderProbeFixture {
    private static final String RESOURCE = "/assets/cinemarr/decoder/h264-high-aac.ts.b64";
    private static volatile byte[] cached;

    public static byte[] bytes() {
        byte[] value = cached;
        if (value == null) {
            synchronized (DecoderProbeFixture.class) {
                value = cached;
                if (value == null) cached = value = load();
            }
        }
        return value.clone();
    }

    private static byte[] load() {
        InputStream input = DecoderProbeFixture.class.getResourceAsStream(RESOURCE);
        if (input == null) throw new IllegalStateException("Bundled decoder probe fixture is missing");
        try {
            ByteArrayOutputStream encoded = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            for (int count; (count = input.read(buffer)) >= 0; ) encoded.write(buffer, 0, count);
            byte[] decoded = Base64.getMimeDecoder().decode(encoded.toByteArray());
            if (decoded.length < 188 || decoded[0] != 0x47) {
                throw new IllegalStateException("Bundled decoder probe fixture is invalid");
            }
            return decoded;
        } catch (IOException | IllegalArgumentException failure) {
            throw new IllegalStateException("Unable to load bundled decoder probe fixture", failure);
        } finally {
            try { input.close(); } catch (IOException ignored) { }
        }
    }

    private DecoderProbeFixture() { }
}
