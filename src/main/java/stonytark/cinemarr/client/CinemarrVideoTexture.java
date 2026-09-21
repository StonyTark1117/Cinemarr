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
    private net.minecraft.client.renderer.RenderType pixelRenderType, detailedRenderType;
    public net.minecraft.client.renderer.RenderType detailedRenderType() {
        if (detailedRenderType == null) detailedRenderType = OwnedRenderType.detailed(location);
        return detailedRenderType;
    }
    public net.minecraft.client.renderer.RenderType pixelRenderType() {
        if (pixelRenderType == null) pixelRenderType = OwnedRenderType.forTexture(location);
        return pixelRenderType;
    }
    /** The beacon shader preserves vertex colors without directional entity lighting. */
    private abstract static class OwnedRenderType extends net.minecraft.client.renderer.RenderType {
        private OwnedRenderType() {
            super("cinemarr_block_video", com.mojang.blaze3d.vertex.DefaultVertexFormat.BLOCK,
                    com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS, 1536, false, false, () -> {}, () -> {});
        }
        static net.minecraft.client.renderer.RenderType detailed(ResourceLocation texture) {
            // Match entityCutoutNoCull without its process-lifetime texture cache
            // or unused entity-outline cache. A TV is drawn as world geometry.
            return create("cinemarr_video_detailed", com.mojang.blaze3d.vertex.DefaultVertexFormat.NEW_ENTITY,
                    com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS, 1536, true, false,
                    CompositeState.builder().setShaderState(RENDERTYPE_ENTITY_CUTOUT_NO_CULL_SHADER)
                            .setTextureState(new TextureStateShard(texture, false, false))
                            .setTransparencyState(NO_TRANSPARENCY).setCullState(NO_CULL)
                            .setLightmapState(LIGHTMAP).setOverlayState(OVERLAY).createCompositeState(false));
        }
        static net.minecraft.client.renderer.RenderType forTexture(ResourceLocation texture) {
            return create("cinemarr_block_video", com.mojang.blaze3d.vertex.DefaultVertexFormat.BLOCK,
                    com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS, 1536, false, false,
                    CompositeState.builder().setShaderState(RENDERTYPE_BEACON_BEAM_SHADER)
                            .setTextureState(new TextureStateShard(texture, false, false))
                            .setTransparencyState(NO_TRANSPARENCY).setCullState(NO_CULL)
                            .setWriteMaskState(COLOR_DEPTH_WRITE).createCompositeState(false));
        }
    }
    private byte[] source;
    private final stonytark.cinemarr.core.video.PresentedFrame presented = new stonytark.cinemarr.core.video.PresentedFrame();
    private long frameRevision;
    private long evidenceRevision = -1;
    private final java.util.Map<java.util.UUID, Derived> derived = new java.util.HashMap<>();
    private static final class Derived {
        final CinemarrVideoTexture texture = new CinemarrVideoTexture();
        byte[] raster;
        long revision = -1;
        int width, height;
        stonytark.cinemarr.core.video.PresentationMode layout;
    }
    public CinemarrVideoTexture forDisplay(stonytark.cinemarr.core.protocol.VideoPackets.SessionState state) {
        if (state.paused() && source != null && evidenceRevision != frameRevision
                && stonytark.cinemarr.core.protocol.ProtocolLimits.displayProbeEnabled()) {
            stonytark.cinemarr.core.video.DisplayFrameEvidence.retain(source);
            evidenceRevision = frameRevision;
        }
        if (state.displaySettings().mapping() == stonytark.cinemarr.core.video.PixelMapping.DETAILED) {
            Derived old=derived.remove(state.televisionId()); if(old!=null)old.texture.close();
            if(derived.isEmpty())presented.releaseRaster();
            return this;
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
        for(Derived value:derived.values())value.texture.close();derived.clear();source=null;presented.clear();pixelRenderType=null;detailedRenderType=null;
        if (texture != null) {
            Minecraft.getInstance().getTextureManager().release(location);
            texture = null;
            width = height = 0;
        }
    }
}
