package stonytark.cinemarr.core.video;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BlockRasterizerTest {
    private byte[] corners() { return new byte[]{(byte)255,0,0,(byte)255, 0,(byte)255,0,(byte)255, 0,0,(byte)255,(byte)255, (byte)255,(byte)255,(byte)255,(byte)255}; }
    @Test void centerIsIndependentGoldenAverageAndEdgesClamp() {
        byte[] single = BlockRasterizer.render(corners(),2,2,1,1,PresentationMode.STRETCH,null);
        assertArrayEquals(new byte[]{(byte)128,(byte)128,(byte)128,(byte)255},single);
        byte[] expanded = BlockRasterizer.render(corners(),2,2,4,4,PresentationMode.STRETCH,null);
        assertEquals(255, expanded[0]&255);
        assertEquals(0, expanded[1]&255);
        // Output (1,1) samples source (.25,.25): weights 9/16,3/16,3/16,1/16.
        assertEquals(159,expanded[20]&255);
        assertEquals(64,expanded[21]&255);
        assertEquals(64,expanded[22]&255);
    }
    @Test void fitProducesOpaqueBarsAndFillUsesCenterCrop() {
        byte[] redGreen = {(byte)255,0,0,(byte)255, 0,(byte)255,0,(byte)255};
        byte[] fit = BlockRasterizer.render(redGreen,2,1,4,4,PresentationMode.FIT,null);
        assertArrayEquals(new byte[]{0,0,0,(byte)255},java.util.Arrays.copyOf(fit,4));
        byte[] fill = BlockRasterizer.render(redGreen,2,1,1,1,PresentationMode.FILL,null);
        assertArrayEquals(new byte[]{(byte)128,(byte)128,0,(byte)255},fill);
    }
    @Test void oddWideAndLineRastersReuseBoundedStorageWithoutChangingSource() {
        byte[] source=corners(), original=source.clone();
        for(int[] size:new int[][]{{4,4},{16,9},{17,11},{2000,20},{1,2048},{2048,1}})
            for(PresentationMode mode:PresentationMode.values()) {
                byte[] reuse=new byte[size[0]*size[1]*4];
                assertSame(reuse,BlockRasterizer.render(source,2,2,size[0],size[1],mode,reuse));
                for(int i=3;i<reuse.length;i+=4)assertEquals(255,reuse[i]&255);
            }
        assertArrayEquals(original,source);
        assertNotSame(source,BlockRasterizer.render(source,2,2,2,2,PresentationMode.STRETCH,source));
        assertThrows(IllegalArgumentException.class,()->BlockRasterizer.render(source,2,2,Integer.MAX_VALUE,2048,PresentationMode.FIT,null));
    }

    @Test void nonSquareAndLineScreensHaveIndependentGoldenColors() {
        // Hand-calculated from the four source corners, not PresentationTransform.
        // Rows are width, height, x, y, and expected RGB. Check aspect bars,
        // odd centers, extreme aspect ratios and both one-cell orientations.
        golden(PresentationMode.FIT, new int[][] {
                {16,9,0,4,0,0,0}, {16,9,7,4,128,99,128},
                {17,11,2,5,0,0,0}, {17,11,3,5,128,0,128},
                {17,11,8,5,128,128,128}, {17,11,13,5,128,255,128},
                {2000,20,989,0,0,0,0}, {2000,20,990,0,255,0,0},
                {2000,20,1000,10,129,140,140},
                {1,17,0,7,0,0,0}, {1,17,0,8,128,128,128},
                {17,1,7,0,0,0,0}, {17,1,8,0,128,128,128}});
        golden(PresentationMode.FILL, new int[][] {
                {16,9,7,4,128,112,128}, {17,11,8,5,128,128,128},
                {2000,20,0,0,130,0,125}, {2000,20,1999,19,130,255,130},
                {1,2048,0,0,128,128,0}, {1,2048,0,2047,128,128,255},
                {2048,1,0,0,128,0,128}, {2048,1,2047,0,128,255,128}});
        golden(PresentationMode.STRETCH, new int[][] {
                {16,9,7,4,128,112,128}, {17,11,8,5,128,128,128},
                {2000,20,999,9,128,127,115}, {2000,20,1000,10,128,128,140},
                {1,2048,0,0,128,128,0}, {1,2048,0,2047,128,128,255},
                {2048,1,0,0,128,0,128}, {2048,1,2047,0,128,255,128}});
    }

    private void golden(PresentationMode mode, int[][] cases) {
        for (int[] value : cases) {
            byte[] raster = BlockRasterizer.render(corners(),2,2,value[0],value[1],mode,null);
            int offset = (value[3]*value[0]+value[2])*4;
            assertArrayEquals(new byte[]{(byte)value[4],(byte)value[5],(byte)value[6],(byte)255},
                    java.util.Arrays.copyOfRange(raster,offset,offset+4),
                    mode+" "+java.util.Arrays.toString(value));
        }
    }
}
