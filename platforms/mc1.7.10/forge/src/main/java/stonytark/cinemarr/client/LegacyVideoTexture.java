package stonytark.cinemarr.client;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.nio.ByteBuffer;

/** One OpenGL texture per decoded watch-party stream, updated on the client thread. */
final class LegacyVideoTexture implements AutoCloseable {
    private int textureId;
    private int width;
    private int height;

    void upload(LegacyDecodedVideoFrame frame) {
        boolean allocate = textureId == 0 || width != frame.width() || height != frame.height();
        if (textureId == 0) textureId = GL11.glGenTextures();
        width = frame.width(); height = frame.height(); bind();
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);
        byte[] rgba = frame.rgbaView(); ByteBuffer pixels = BufferUtils.createByteBuffer(rgba.length); pixels.put(rgba).flip();
        if (allocate) GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
        else GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
    }

    void bind() { GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId); }
    boolean ready() { return textureId != 0; }
    int width() { return width; }
    int height() { return height; }
    @Override public void close() { if (textureId != 0) GL11.glDeleteTextures(textureId); textureId = width = height = 0; }
}
