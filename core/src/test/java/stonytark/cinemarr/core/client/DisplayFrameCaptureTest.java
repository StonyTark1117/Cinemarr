package stonytark.cinemarr.core.client;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import stonytark.cinemarr.core.protocol.ProtocolLimits;
import stonytark.cinemarr.core.protocol.VideoPackets;
import stonytark.cinemarr.core.screen.ScreenFacing;
import stonytark.cinemarr.core.video.PresentationMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class DisplayFrameCaptureTest {
    @TempDir Path directory;
    @AfterEach void clear() {
        DisplayFrameCapture.reset();
        for(String name:new String[]{ProtocolLimits.ACCEPTANCE_ENABLED_PROPERTY,ProtocolLimits.ACCEPTANCE_VIDEO_PROBE_PROPERTY,
                ProtocolLimits.ACCEPTANCE_AUDIO_CONTROL_FILE_PROPERTY,"cinemarr.acceptance.displayProbe"}) System.clearProperty(name);
    }
    private VideoPackets.SessionState state() {
        return new VideoPackets.SessionState(UUID.randomUUID(),42,UUID.randomUUID(),0,VideoPackets.SessionStatus.PAUSED,
                null,0,0,true,PresentationMode.FIT,4,4,new byte[]{-1,-1},ScreenFacing.SOUTH,0,20,100,
                Collections.emptyList(),-1,-1,0,true,"");
    }
    @Test void requestIsGatedBoundedAndNeverReplaysAfterReset() {
        DisplayFrameCapture.request("video:display-frame:42:1");assertFalse(DisplayFrameCapture.requested(42));
        enable();
        assertThrows(IllegalArgumentException.class,()->DisplayFrameCapture.request("video:display-frame:42:../escape"));
        DisplayFrameCapture.request("video:display-frame:42:1");assertTrue(DisplayFrameCapture.requested(42));
        assertFalse(DisplayFrameCapture.requested(43));DisplayFrameCapture.reset();assertFalse(DisplayFrameCapture.requested(42));
    }
    private void enable() {
        System.setProperty(ProtocolLimits.ACCEPTANCE_ENABLED_PROPERTY,"true");
        System.setProperty(ProtocolLimits.ACCEPTANCE_VIDEO_PROBE_PROPERTY,"true");
        System.setProperty("cinemarr.acceptance.displayProbe","true");
        System.setProperty(ProtocolLimits.ACCEPTANCE_AUDIO_CONTROL_FILE_PROPERTY,directory.resolve("client.control").toString());
    }
    @Test void backgroundCaptureIsOptInScopedAndClearedOnDisconnect() {
        DisplayFrameCapture.background("video:display-background:42:true");assertFalse(DisplayFrameCapture.isSuppressed(42));
        enable();DisplayFrameCapture.background("video:display-background:42:true");
        assertTrue(DisplayFrameCapture.isSuppressed(42));assertFalse(DisplayFrameCapture.isSuppressed(43));
        DisplayFrameCapture.background("video:display-background:43:false");assertTrue(DisplayFrameCapture.isSuppressed(42));
        assertThrows(IllegalArgumentException.class,()->DisplayFrameCapture.background("video:display-background:42:maybe"));
        DisplayFrameCapture.reset();assertFalse(DisplayFrameCapture.isSuppressed(42));
    }
    @Test void receiptPublishesExactSourceAndCannotOverwriteEarlierEvidence() throws Exception {
        enable();byte[] source={1,2,3,-1};double[] camera={22.5,102.62,7.5,180,0,70,101,0,1};
        DisplayFrameCapture.request("video:display-frame:42:123");
        assertTrue(DisplayFrameCapture.capture(state(),source,1,1,4,0,camera).contains("complete=true"));
        Path raw=directory.resolve("client.control.frame-123.rgba");assertArrayEquals(source,Files.readAllBytes(raw));
        String metadata=new String(Files.readAllBytes(directory.resolve("client.control.frame-123.json")),StandardCharsets.UTF_8);
        assertTrue(metadata.contains("\"guiOpen\":false"));assertTrue(metadata.contains("\"hudHidden\":true"));
        assertTrue(metadata.contains("\"sourceWidth\":1"));assertTrue(metadata.contains("\"mask\":\"ffff\""));
        DisplayFrameCapture.request("video:display-frame:42:123");
        assertTrue(DisplayFrameCapture.capture(state(),new byte[]{4,5,6,-1},1,1,4,0,camera).contains("complete=false"));
        assertArrayEquals(source,Files.readAllBytes(raw));assertFalse(DisplayFrameCapture.requested(42));
    }
}
