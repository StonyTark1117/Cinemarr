package stonytark.cinemarr.core.video;

import stonytark.cinemarr.core.client.DecodedBufferBudget;

/** One retained immutable-by-ownership source frame and one reusable block raster per TV stream. */
public final class PresentedFrame {
    public static final long MAX_RETAINED_BYTES = DecodedBufferBudget.MAX_VIDEO_FRAME_BYTES;
    private byte[] source, raster;
    private int width, height, rasterWidth, rasterHeight;
    private PresentationMode layout;
    private boolean dirty;
    public void accept(byte[] rgba, int width, int height) {
        if (rgba == null || rgba.length != DecodedBufferBudget.rgbaBytes(width,height))
            throw new IllegalArgumentException("Invalid presented frame");
        if (raster != null && (long) rgba.length + raster.length > MAX_RETAINED_BYTES) releaseRaster();
        this.source=rgba; this.width=width; this.height=height; dirty=true;
    }
    public static long maximumSourceBytes(int width, int height) {
        return MAX_RETAINED_BYTES - DecodedBufferBudget.rgbaBytes(width, height);
    }
    public boolean canRaster(int width, int height) {
        return source != null && source.length <= maximumSourceBytes(width, height);
    }
    public byte[] raster(int width, int height, PresentationMode layout) {
        if (source == null) return null;
        if (!canRaster(width, height))
            throw new IllegalArgumentException("Retained frame and block raster exceed memory budget");
        if (dirty || raster == null || rasterWidth != width || rasterHeight != height || this.layout != layout) {
            raster=BlockRasterizer.render(source,this.width,this.height,width,height,layout,raster);
            rasterWidth=width; rasterHeight=height; this.layout=layout; dirty=false;
        }
        return raster;
    }
    public void releaseRaster() { raster=null; }
    public void clear() { source=null; raster=null; dirty=false; }
    public long retainedBytes() { return (source==null?0:source.length)+(raster==null?0:raster.length); }
}
