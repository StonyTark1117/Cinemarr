package stonytark.cinemarr.core.video;

import stonytark.cinemarr.core.client.DecodedBufferBudget;

/** Deterministic RGBA sampling at output-cell centers. Screen holes are applied by the mesh. */
public final class BlockRasterizer {
    private BlockRasterizer() {}
    public static byte[] render(byte[] source, int sourceWidth, int sourceHeight, int width, int height,
                                PresentationMode mode, byte[] reuse) {
        int sourceBytes = DecodedBufferBudget.rgbaBytes(sourceWidth, sourceHeight);
        int bytes = DecodedBufferBudget.rgbaBytes(width, height);
        if (source == null || source.length != sourceBytes) throw new IllegalArgumentException("Invalid source raster");
        byte[] output = reuse != null && reuse != source && reuse.length == bytes ? reuse : new byte[bytes];
        PresentationTransform transform = PresentationTransform.create(sourceWidth, sourceHeight, width, height, mode);
        for (int y = 0; y < height; y++) {
            DecodedBufferBudget.checkCancelled();
            for (int x = 0; x < width; x++) {
                int dst = (y * width + x) * 4;
                if (!transform.samplesSource(x + 0.5, y + 0.5, sourceWidth, sourceHeight)) {
                    output[dst] = output[dst + 1] = output[dst + 2] = 0;
                    output[dst + 3] = (byte) 255;
                    continue;
                }
                double sx = Math.max(0, Math.min(sourceWidth - 1, transform.sourceX(x + 0.5) - 0.5));
                double sy = Math.max(0, Math.min(sourceHeight - 1, transform.sourceY(y + 0.5) - 0.5));
                int x0 = (int) sx, y0 = (int) sy;
                int x1 = Math.min(sourceWidth - 1, x0 + 1), y1 = Math.min(sourceHeight - 1, y0 + 1);
                double fx = sx - x0, fy = sy - y0;
                for (int c = 0; c < 3; c++) {
                    double top = (source[(y0 * sourceWidth + x0) * 4 + c] & 255) * (1 - fx)
                            + (source[(y0 * sourceWidth + x1) * 4 + c] & 255) * fx;
                    double bottom = (source[(y1 * sourceWidth + x0) * 4 + c] & 255) * (1 - fx)
                            + (source[(y1 * sourceWidth + x1) * 4 + c] & 255) * fx;
                    output[dst + c] = (byte) Math.round(top * (1 - fy) + bottom * fy);
                }
                output[dst + 3] = (byte) 255;
            }
        }
        return output;
    }
}
