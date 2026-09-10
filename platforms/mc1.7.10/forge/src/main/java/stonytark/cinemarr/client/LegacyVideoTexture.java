package stonytark.cinemarr.client;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.nio.ByteBuffer;

/** One OpenGL texture per decoded watch-party stream, updated on the client thread. */
final class LegacyVideoTexture implements AutoCloseable {
    private int textureId;
    private byte[] source;
    private final stonytark.cinemarr.core.video.PresentedFrame presented = new stonytark.cinemarr.core.video.PresentedFrame();
    private long frameRevision;
    private final java.util.Map<java.util.UUID, Derived> derived = new java.util.HashMap<>();
    private static final class Derived {
        final LegacyVideoTexture texture = new LegacyVideoTexture();
        byte[] raster;
        long revision = -1;
        int width, height;
        stonytark.cinemarr.core.video.PresentationMode layout;
    }
    public LegacyVideoTexture forDisplay(stonytark.cinemarr.core.protocol.VideoPackets.SessionState state) {
        if (state.displaySettings().mapping() == stonytark.cinemarr.core.video.PixelMapping.DETAILED) {
            Derived old=derived.remove(state.televisionId()); if(old!=null)old.texture.close(); return this;
        }
        if (source == null) return this;
        Derived value=derived.get(state.televisionId());
        if(value==null){value=new Derived();derived.put(state.televisionId(),value);}
        if(value.revision!=frameRevision||value.width!=state.screenWidth()||value.height!=state.screenHeight()||value.layout!=state.presentationMode()) {
            value.raster=presented.raster(state.screenWidth(),state.screenHeight(),state.presentationMode());
            value.texture.uploadRaw(state.screenWidth(),state.screenHeight(),value.raster,true);
            value.revision=frameRevision; value.width=state.screenWidth(); value.height=state.screenHeight(); value.layout=state.presentationMode();
        }
        return value.texture;
    }
    public void retainDisplays(java.util.Set<java.util.UUID> visible) {
        java.util.Iterator<java.util.Map.Entry<java.util.UUID,Derived>> it=derived.entrySet().iterator();
        while(it.hasNext()){java.util.Map.Entry<java.util.UUID,Derived> entry=it.next();if(!visible.contains(entry.getKey())){entry.getValue().texture.close();it.remove();}}
    }
    private int width;
    private int height;

    void upload(LegacyDecodedVideoFrame frame) {
        source=frame.rgbaView(); frameRevision++;
        presented.accept(source, frame.width(), frame.height());
        uploadRaw(frame.width(),frame.height(),source,false);
    }
    private void uploadRaw(int frameWidth, int frameHeight, byte[] rgba, boolean nearest) {
        boolean allocate = textureId == 0 || width != frameWidth || height != frameHeight;
        if (textureId == 0) textureId = GL11.glGenTextures();
        width = frameWidth; height = frameHeight; bind();
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, nearest ? GL11.GL_NEAREST : GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, nearest ? GL11.GL_NEAREST : GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);
        ByteBuffer pixels = BufferUtils.createByteBuffer(rgba.length); pixels.put(rgba).flip();
        if (allocate) GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
        else GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
    }

    void bind() { GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId); }
    boolean ready() { return textureId != 0; }
    int width() { return width; }
    int height() { return height; }
    @Override public void close() {
        for(Derived value:derived.values())value.texture.close();derived.clear();source=null;presented.clear(); if (textureId != 0) GL11.glDeleteTextures(textureId); textureId = width = height = 0; }
}
