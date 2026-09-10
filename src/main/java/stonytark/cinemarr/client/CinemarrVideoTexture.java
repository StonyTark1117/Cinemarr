package stonytark.cinemarr.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.system.MemoryUtil;
import stonytark.cinemarr.Cinemarr;
import stonytark.cinemarr.mixin.client.NativeImageAccessor;

/** A single TV texture updated with one native-memory copy on the render thread. */
public final class CinemarrVideoTexture implements AutoCloseable {
    private static final java.util.concurrent.atomic.AtomicLong IDS=new java.util.concurrent.atomic.AtomicLong();
    private final ResourceLocation location=VideoResourceLocations.create(Cinemarr.MODID,"dynamic/video_frame_"+Long.toUnsignedString(IDS.incrementAndGet(),36));
    private DynamicTexture texture;
    private byte[] source;
    private final stonytark.cinemarr.core.video.PresentedFrame presented = new stonytark.cinemarr.core.video.PresentedFrame();
    private long frameRevision;
    private final java.util.Map<java.util.UUID, Derived> derived = new java.util.HashMap<>();
    private static final class Derived {
        final CinemarrVideoTexture texture = new CinemarrVideoTexture();
        byte[] raster;
        long revision = -1;
        int width, height;
        stonytark.cinemarr.core.video.PresentationMode layout;
    }
    public CinemarrVideoTexture forDisplay(stonytark.cinemarr.core.protocol.VideoPackets.SessionState state) {
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

    public void upload(DecodedVideoFrame frame) {
        source=frame.rgbaView(); frameRevision++;
        presented.accept(source, frame.width(), frame.height());
        uploadRaw(frame.width(),frame.height(),source,false);
    }
    private void uploadRaw(int frameWidth, int frameHeight, byte[] rgba, boolean nearest) {
        RenderSystem.assertOnRenderThread();
        if (texture == null || width != frameWidth || height != frameHeight) {
            if (texture != null) Minecraft.getInstance().getTextureManager().release(location);
            width = frameWidth;
            height = frameHeight;
            texture = new DynamicTexture(new NativeImage(width, height, false));
            Minecraft.getInstance().getTextureManager().register(location, texture);
        }
        NativeImage image = texture.getPixels();
        if (image == null) throw new IllegalStateException("Video texture was disposed");
        MemoryUtil.memByteBuffer(((NativeImageAccessor) (Object) image).cinemarr$pixels(), rgba.length).put(0, rgba);
        texture.setFilter(false, false);
        texture.upload();
    }

    public boolean ready() { return texture != null; }
    public int width() { return width; }
    public int height() { return height; }
    public ResourceLocation location(){return location;}

    @Override public void close() {
        for(Derived value:derived.values())value.texture.close();derived.clear();source=null;presented.clear();
        if (texture != null) {
            Minecraft.getInstance().getTextureManager().release(location);
            texture = null;
            width = height = 0;
        }
    }
}
