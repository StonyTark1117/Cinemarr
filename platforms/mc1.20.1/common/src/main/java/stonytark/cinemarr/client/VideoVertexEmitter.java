package stonytark.cinemarr.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix4f;

final class VideoVertexEmitter {
    static void vertex(VertexConsumer out, Matrix4f matrix, float x, float y, float z,
                       float u, float v, float nx, float ny, float nz) {
        out.vertex(matrix,x,y,z).color(255,255,255,255).uv(u,v)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0x00F000F0).normal(nx,ny,nz).endVertex();
    }
    private VideoVertexEmitter() {}
}
