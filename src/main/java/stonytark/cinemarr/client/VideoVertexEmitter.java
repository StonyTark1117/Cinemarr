package stonytark.cinemarr.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;

final class VideoVertexEmitter {
    static void vertex(VertexConsumer out, Matrix4f matrix, float x, float y, float z,
                       float u, float v, float nx, float ny, float nz) {
        out.addVertex(matrix,x,y,z).setColor(255,255,255,255).setUv(u,v)
                .setLight(0x00F000F0);
    }
    private VideoVertexEmitter() {}
}
