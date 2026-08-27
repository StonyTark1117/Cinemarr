package stonytark.cinemarr.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import stonytark.cinemarr.core.protocol.VideoPackets;
import stonytark.cinemarr.core.platform.CinemarrSettings;
import stonytark.cinemarr.core.screen.ScreenFacing;
import stonytark.cinemarr.core.screen.ScreenMaskMesher;
import stonytark.cinemarr.core.video.PresentationTransform;

import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Set;

/** Batched world-space quads; ordinary Screen Pixel blocks never gain block entities. */
public final class CinemarrVideoRenderer {
    private final Map<UUID,MeshCache> meshes=new HashMap<>();

    public void submit(PoseStack pose, Vec3 camera, SubmitNodeCollector submits,
                       CinemarrVideoPlaybackManager playback, CinemarrVideoClientState clientState) {
        if (!CinemarrSettings.enabled()) return;
        pose.pushPose();
        pose.translate(-camera.x, -camera.y, -camera.z);
        Set<UUID> visible=new LinkedHashSet<>();
        for(VideoPackets.SessionState state:clientState.televisions()){
            if(state.item()==null||state.status()==VideoPackets.SessionStatus.IDLE)continue;
            CinemarrVideoPlayback pipeline=playback.pipeline(new CinemarrVideoClientState.StreamKey(state.sessionId(),state.generation()));
            if(pipeline==null||!pipeline.texture().ready())continue;visible.add(state.televisionId());
            MeshCache mesh=updateMesh(state);PresentationTransform transform=PresentationTransform.create(pipeline.texture().width(),pipeline.texture().height(),state.screenWidth(),state.screenHeight(),state.presentationMode());
            RenderType type=RenderTypes.entityCutout(pipeline.texture().location());
            int sourceWidth=pipeline.texture().width(),sourceHeight=pipeline.texture().height();
            submits.submitCustomGeometry(pose,type,(entry,vertices)->{
                Matrix4f matrix=entry.pose();
                for(ScreenMaskMesher.Rectangle rectangle:mesh.rectangles)draw(vertices,matrix,state,rectangle,transform,sourceWidth,sourceHeight);
            });
        }
        pose.popPose();meshes.keySet().retainAll(visible);
    }

    private MeshCache updateMesh(VideoPackets.SessionState state) {
        byte[] nextMask = state.visibilityMask();
        MeshCache current=meshes.get(state.televisionId());
        if(current==null||current.width!=state.screenWidth()||current.height!=state.screenHeight()||!java.util.Arrays.equals(current.mask,nextMask)){current=new MeshCache(state.screenWidth(),state.screenHeight(),nextMask,ScreenMaskMesher.mesh(state.screenWidth(),state.screenHeight(),nextMask));meshes.put(state.televisionId(),current);}return current;
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
        VideoVertexEmitter.vertex(out,matrix,(float)point.x,(float)point.y,(float)point.z,u,v,nx,ny,nz);
    }
    private record WorldPoint(double x,double y,double z) {}
    private record MeshCache(int width,int height,byte[] mask,List<ScreenMaskMesher.Rectangle> rectangles){MeshCache{mask=mask.clone();rectangles=List.copyOf(rectangles);}}
}
