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
    public static final ResourceLocation LOCATION = ResourceLocation.fromNamespaceAndPath(Cinemarr.MODID, "dynamic/video_frame");
    private DynamicTexture texture;
    private int width;
    private int height;

    public void upload(DecodedVideoFrame frame) {
        RenderSystem.assertOnRenderThread();
        if (texture == null || width != frame.width() || height != frame.height()) {
            width = frame.width();
            height = frame.height();
            texture = new DynamicTexture(new NativeImage(width, height, false));
            Minecraft.getInstance().getTextureManager().register(LOCATION, texture);
        }
        NativeImage image = texture.getPixels();
        if (image == null) throw new IllegalStateException("Video texture was disposed");
        byte[] rgba = frame.rgbaView();
        MemoryUtil.memByteBuffer(((NativeImageAccessor) (Object) image).cinemarr$pixels(), rgba.length).put(0, rgba);
        texture.upload();
    }

    public boolean ready() { return texture != null; }
    public int width() { return width; }
    public int height() { return height; }

    @Override public void close() {
        if (texture != null) {
            Minecraft.getInstance().getTextureManager().release(LOCATION);
            texture = null;
            width = height = 0;
        }
    }
}
