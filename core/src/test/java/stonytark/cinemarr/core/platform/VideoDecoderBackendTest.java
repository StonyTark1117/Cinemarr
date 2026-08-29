package stonytark.cinemarr.core.platform;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class VideoDecoderBackendTest {
    @Test
    void acceptsOnlyUserFacingConfigurationModes() {
        assertEquals(VideoDecoderBackend.SOFTWARE, VideoDecoderBackend.parse("software"));
        assertEquals(VideoDecoderBackend.AUTO, VideoDecoderBackend.parse("AUTO"));
        assertEquals(VideoDecoderBackend.VAAPI, VideoDecoderBackend.parse("vaapi"));

        assertEquals(VideoDecoderBackend.SOFTWARE, VideoDecoderBackend.parse("qsv"));
        assertEquals(VideoDecoderBackend.SOFTWARE, VideoDecoderBackend.parse("cuda"));
        assertEquals(VideoDecoderBackend.SOFTWARE, VideoDecoderBackend.parse("d3d11va"));
        assertEquals(VideoDecoderBackend.SOFTWARE, VideoDecoderBackend.parse("dxva2"));

        assertEquals(VideoDecoderBackend.CUDA, VideoDecoderBackend.parseInternal("cuda"));
        assertEquals(VideoDecoderBackend.D3D11VA, VideoDecoderBackend.parseInternal("d3d11va"));
    }

    @Test
    void configScreenCyclesOnlyUserFacingModes() {
        assertEquals(VideoDecoderBackend.AUTO, VideoDecoderBackend.SOFTWARE.next());
        assertEquals(VideoDecoderBackend.VAAPI, VideoDecoderBackend.AUTO.next());
        assertEquals(VideoDecoderBackend.SOFTWARE, VideoDecoderBackend.VAAPI.next());
        assertEquals(VideoDecoderBackend.SOFTWARE, VideoDecoderBackend.CUDA.next());
    }
}
