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
}
