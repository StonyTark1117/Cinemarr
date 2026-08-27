package stonytark.cinemarr.client;

import net.minecraft.client.renderer.entity.RenderManager;
import org.lwjgl.opengl.GL11;
import stonytark.cinemarr.Cinemarr;
import stonytark.cinemarr.core.platform.CinemarrSettings;
import stonytark.cinemarr.core.protocol.ProtocolLimits;
import stonytark.cinemarr.core.protocol.VideoPackets;
import stonytark.cinemarr.core.screen.ScreenFacing;
import stonytark.cinemarr.core.screen.ScreenMaskMesher;
import stonytark.cinemarr.core.video.PresentationTransform;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Batches each masked legacy TV into textured world-space rectangles. */
final class LegacyVideoRenderer {
    private final Map<UUID, MeshCache> meshes = new HashMap<UUID, MeshCache>();
    private final Map<UUID, String> acceptanceFrames = new HashMap<UUID, String>();
    void render(LegacyVideoPlaybackManager playback, LegacyVideoClientState state) {
        if (!CinemarrSettings.enabled()) return;
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS); GL11.glPushMatrix();
        GL11.glTranslated(-RenderManager.renderPosX, -RenderManager.renderPosY, -RenderManager.renderPosZ);
        GL11.glEnable(GL11.GL_TEXTURE_2D); GL11.glDisable(GL11.GL_LIGHTING); GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glColor4f(1, 1, 1, 1);
        for (VideoPackets.SessionState television : state.televisions()) {
            if (television.item() == null || television.status() == VideoPackets.SessionStatus.IDLE) continue;
            LegacyVideoPlayback pipeline = playback.pipeline(new LegacyVideoClientState.StreamKey(television.sessionId(), television.generation()));
            if (pipeline == null || !pipeline.texture().ready()) continue;
            pipeline.texture().bind(); MeshCache mesh = mesh(television);
            PresentationTransform transform = PresentationTransform.create(pipeline.texture().width(), pipeline.texture().height(),
                    television.screenWidth(), television.screenHeight(), television.presentationMode());
            GL11.glBegin(GL11.GL_QUADS);
            for (ScreenMaskMesher.Rectangle rectangle : mesh.rectangles) draw(television, rectangle, transform,
                    pipeline.texture().width(), pipeline.texture().height());
            GL11.glEnd();
            if (ProtocolLimits.videoProbeEnabled() && !pipeline.lastFrameSha256().equals(
                    acceptanceFrames.put(television.televisionId(), pipeline.lastFrameSha256()))) {
                Cinemarr.LOGGER.info("Acceptance video rendered: television={} frameSha256={} ptsUs={} rectangles={}",
                        television.televisionId(), pipeline.lastFrameSha256(), pipeline.lastPresentedUs(), mesh.rectangles.size());
            }
        }
        GL11.glPopMatrix(); GL11.glPopAttrib();
    }
    private MeshCache mesh(VideoPackets.SessionState state) {
        byte[] mask = state.visibilityMask(); MeshCache current = meshes.get(state.televisionId());
        if (current == null || current.width != state.screenWidth() || current.height != state.screenHeight() || !Arrays.equals(current.mask, mask)) {
            current = new MeshCache(state.screenWidth(), state.screenHeight(), mask, ScreenMaskMesher.mesh(state.screenWidth(), state.screenHeight(), mask));
            meshes.put(state.televisionId(), current);
        }
        return current;
    }
    private static void draw(VideoPackets.SessionState state, ScreenMaskMesher.Rectangle rectangle,
                             PresentationTransform transform, int sourceWidth, int sourceHeight) {
        double x0 = Math.max(rectangle.x(), transform.offsetX()), x1 = Math.min(rectangle.x() + rectangle.width(), transform.offsetX() + sourceWidth * transform.scaleX());
        double y0 = Math.max(rectangle.y(), transform.offsetY()), y1 = Math.min(rectangle.y() + rectangle.height(), transform.offsetY() + sourceHeight * transform.scaleY());
        if (x1 <= x0 || y1 <= y0) return;
        float u0 = (float) (transform.sourceX(x0) / sourceWidth), u1 = (float) (transform.sourceX(x1) / sourceWidth);
        float v0 = 1.0F - (float) (transform.sourceY(y0) / sourceHeight), v1 = 1.0F - (float) (transform.sourceY(y1) / sourceHeight);
        point(state, x0, y0, u0, v0); point(state, x1, y0, u1, v0); point(state, x1, y1, u1, v1); point(state, x0, y1, u0, v1);
    }
    private static void point(VideoPackets.SessionState state, double u, double v, float textureU, float textureV) {
        double worldU = state.minimumU() + u, worldV = state.minimumV() + v, offset = 0.002;
        double x, y, z;
        switch (state.screenFacing()) {
            case NORTH: x = worldU; y = worldV; z = state.screenPlane() - offset; break;
            case SOUTH: x = worldU; y = worldV; z = state.screenPlane() + 1 + offset; break;
            case WEST: x = state.screenPlane() - offset; y = worldV; z = worldU; break;
            case EAST: x = state.screenPlane() + 1 + offset; y = worldV; z = worldU; break;
            case DOWN: x = worldU; y = state.screenPlane() - offset; z = worldV; break;
            default: x = worldU; y = state.screenPlane() + 1 + offset; z = worldV;
        }
        GL11.glTexCoord2f(textureU, textureV); GL11.glVertex3d(x, y, z);
    }
    private static final class MeshCache {
        final int width, height; final byte[] mask; final List<ScreenMaskMesher.Rectangle> rectangles;
        MeshCache(int width, int height, byte[] mask, List<ScreenMaskMesher.Rectangle> rectangles) {
            this.width = width; this.height = height; this.mask = mask.clone(); this.rectangles = rectangles;
        }
    }
}
