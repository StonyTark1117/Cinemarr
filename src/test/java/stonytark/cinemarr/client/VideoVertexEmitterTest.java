package stonytark.cinemarr.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VideoVertexEmitterTest {
    @Test void emitsCompleteDetailedShaderVertices() {
        emitQuad(DefaultVertexFormat.NEW_ENTITY);
    }

    @Test void remainsCompatibleWithPixelShaderVertices() {
        emitQuad(DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP);
    }

    private static void emitQuad(VertexFormat format) {
        try (ByteBufferBuilder memory = new ByteBufferBuilder(1024)) {
            BufferBuilder vertices = new BufferBuilder(memory, VertexFormat.Mode.QUADS, format);
            Matrix4f transform = new Matrix4f();
            VideoVertexEmitter.vertex(vertices, transform, 0, 0, 0, 0, 0, 0, 0, 1);
            VideoVertexEmitter.vertex(vertices, transform, 1, 0, 0, 1, 0, 0, 0, 1);
            VideoVertexEmitter.vertex(vertices, transform, 1, 1, 0, 1, 1, 0, 0, 1);
            VideoVertexEmitter.vertex(vertices, transform, 0, 1, 0, 0, 1, 0, 0, 1);
            // BufferBuilder validates all shader-required elements at each vertex boundary.
            try (MeshData mesh = vertices.buildOrThrow()) {
                assertEquals(4 * format.getVertexSize(), mesh.vertexBuffer().remaining());
            }
        }
    }
}
