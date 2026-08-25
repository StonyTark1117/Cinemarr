package stonytark.cinemarr.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import stonytark.cinemarr.core.protocol.VideoPackets;
import stonytark.cinemarr.core.screen.ScreenFacing;
import stonytark.cinemarr.core.screen.ScreenMaskMesher;
import stonytark.cinemarr.core.video.PresentationTransform;

import java.util.List;
import java.util.UUID;

/** Batched world-space quads; ordinary Screen Pixel blocks never gain block entities. */
public final class CinemarrVideoRenderer {
    private UUID televisionId;
    private byte[] mask;
    private int width;
    private int height;
    private List<ScreenMaskMesher.Rectangle> rectangles = List.of();

    public void render(RenderLevelStageEvent event, CinemarrVideoPlayback playback, CinemarrVideoClientState clientState) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || !playback.texture().ready()) return;
        VideoPackets.SessionState state = clientState.session();
        if (state == null || state.item() == null || state.status() == VideoPackets.SessionStatus.IDLE) return;
        updateMesh(state);
        PresentationTransform transform = PresentationTransform.create(playback.texture().width(), playback.texture().height(),
                state.screenWidth(), state.screenHeight(), state.presentationMode());
        RenderType type = RenderType.entityCutoutNoCull(CinemarrVideoTexture.LOCATION);
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer vertices = buffers.getBuffer(type);
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        var camera = event.getCamera().getPosition();
        pose.translate(-camera.x, -camera.y, -camera.z);
        Matrix4f matrix = pose.last().pose();
        for (ScreenMaskMesher.Rectangle rectangle : rectangles) draw(vertices, matrix, state, rectangle, transform,
                playback.texture().width(), playback.texture().height());
        pose.popPose();
        buffers.endBatch(type);
    }

    private void updateMesh(VideoPackets.SessionState state) {
        byte[] nextMask = state.visibilityMask();
        if (!state.televisionId().equals(televisionId) || state.screenWidth() != width || state.screenHeight() != height
                || !java.util.Arrays.equals(mask, nextMask)) {
            televisionId = state.televisionId(); width = state.screenWidth(); height = state.screenHeight(); mask = nextMask;
            rectangles = ScreenMaskMesher.mesh(width, height, mask);
        }
    }

    private static void draw(VertexConsumer out, Matrix4f matrix, VideoPackets.SessionState state,
                             ScreenMaskMesher.Rectangle rectangle, PresentationTransform transform,
                             int sourceWidth, int sourceHeight) {
        double x0 = Math.max(rectangle.x(), transform.offsetX());
        double x1 = Math.min(rectangle.x() + rectangle.width(), transform.offsetX() + sourceWidth * transform.scaleX());
        double y0 = Math.max(rectangle.y(), transform.offsetY());
        double y1 = Math.min(rectangle.y() + rectangle.height(), transform.offsetY() + sourceHeight * transform.scaleY());
        if (x1 <= x0 || y1 <= y0) return;
        float u0 = (float) (transform.sourceX(x0) / sourceWidth);
        float u1 = (float) (transform.sourceX(x1) / sourceWidth);
        float v0 = 1.0f - (float) (transform.sourceY(y0) / sourceHeight);
        float v1 = 1.0f - (float) (transform.sourceY(y1) / sourceHeight);
        WorldPoint lowerLeft = point(state, x0, y0, 0.002);
        WorldPoint lowerRight = point(state, x1, y0, 0.002);
        WorldPoint upperRight = point(state, x1, y1, 0.002);
        WorldPoint upperLeft = point(state, x0, y1, 0.002);
        ScreenFacing facing = state.screenFacing();
        float nx = facing == ScreenFacing.EAST ? 1 : facing == ScreenFacing.WEST ? -1 : 0;
        float ny = facing == ScreenFacing.UP ? 1 : facing == ScreenFacing.DOWN ? -1 : 0;
        float nz = facing == ScreenFacing.SOUTH ? 1 : facing == ScreenFacing.NORTH ? -1 : 0;
        vertex(out,matrix,lowerLeft,u0,v0,nx,ny,nz); vertex(out,matrix,lowerRight,u1,v0,nx,ny,nz);
        vertex(out,matrix,upperRight,u1,v1,nx,ny,nz); vertex(out,matrix,upperLeft,u0,v1,nx,ny,nz);
    }

    private static WorldPoint point(VideoPackets.SessionState state, double u, double v, double offset) {
        double worldU = state.minimumU() + u, worldV = state.minimumV() + v;
        return switch (state.screenFacing()) {
            case NORTH -> new WorldPoint(worldU, worldV, state.screenPlane() - offset);
            case SOUTH -> new WorldPoint(worldU, worldV, state.screenPlane() + 1 + offset);
            case WEST -> new WorldPoint(state.screenPlane() - offset, worldV, worldU);
            case EAST -> new WorldPoint(state.screenPlane() + 1 + offset, worldV, worldU);
            case DOWN -> new WorldPoint(worldU, state.screenPlane() - offset, worldV);
            case UP -> new WorldPoint(worldU, state.screenPlane() + 1 + offset, worldV);
        };
    }

    private static void vertex(VertexConsumer out,Matrix4f matrix,WorldPoint point,float u,float v,float nx,float ny,float nz){
        out.addVertex(matrix,(float)point.x,(float)point.y,(float)point.z).setColor(255,255,255,255).setUv(u,v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(0x00F000F0).setNormal(nx,ny,nz);
    }
    private record WorldPoint(double x,double y,double z) {}
}
