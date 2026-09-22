package stonytark.cinemarr.client;

import org.junit.jupiter.api.Test;
import stonytark.cinemarr.core.protocol.VideoPackets;
import stonytark.cinemarr.core.screen.ScreenFacing;
import stonytark.cinemarr.core.video.PresentedFrame;
import stonytark.cinemarr.core.video.PresentationMode;
import java.util.Collections;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class LegacyVideoTextureTest {
    @Test void detailedAdapterReleasesCpuRasterWithoutDroppingPausedSource() throws Exception {
        LegacyVideoTexture texture=new LegacyVideoTexture();
        java.lang.reflect.Field field=LegacyVideoTexture.class.getDeclaredField("presented");field.setAccessible(true);
        PresentedFrame frame=(PresentedFrame)field.get(texture);
        frame.accept(new byte[64],4,4);frame.raster(17,11,PresentationMode.FIT);
        VideoPackets.SessionState state=new VideoPackets.SessionState(UUID.randomUUID(),1,UUID.randomUUID(),1,
                VideoPackets.SessionStatus.PAUSED,null,0,0,true,PresentationMode.FIT,17,11,new byte[0],
                ScreenFacing.NORTH,0,0,0,Collections.emptyList(),-1,-1,0,true,"");
        assertSame(texture,texture.forDisplay(state));assertEquals(64,frame.retainedBytes());
        frame.raster(17,11,PresentationMode.FILL);texture.retainDisplays(Collections.emptySet());assertEquals(64,frame.retainedBytes());
        texture.close();assertEquals(0,frame.retainedBytes());
    }
}
