package stonytark.cinemarr.core.video;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BlockRasterizerTest {
    private byte[] corners() { return new byte[]{(byte)255,0,0,(byte)255, 0,(byte)255,0,(byte)255, 0,0,(byte)255,(byte)255, (byte)255,(byte)255,(byte)255,(byte)255}; }
    @Test void fullMatrixMatchesIndependentAnalyticColorFieldAtEveryCell() {
        // Affine source colors have an analytic bilinear interpolant, requiring neither
        // the production transform nor a second implementation of its four-tap sampler.
        int sw=7,sh=5;byte[] source=new byte[sw*sh*4];
        for(int y=0;y<sh;y++)for(int x=0;x<sw;x++) {
            int i=(y*sw+x)*4;source[i]=(byte)(10+20*x);source[i+1]=(byte)(20+30*y);
            source[i+2]=(byte)(5+10*x+15*y);source[i+3]=(byte)255;
        }
        for(int[] size:new int[][]{{4,4},{16,9},{17,11},{2000,20},{1,2048},{2048,1}}) {
            int w=size[0],h=size[1];
            for(PresentationMode mode:PresentationMode.values()) {
                byte[] actual=BlockRasterizer.render(source,sw,sh,w,h,mode,null);
                double aspect=(double)w*sh/(h*sw);
                for(int y=0;y<h;y++)for(int x=0;x<w;x++) {
                    double u=(x+0.5)/w-0.5,v=(y+0.5)/h-0.5;
                    if(mode==PresentationMode.FIT){if(aspect>1)u*=aspect;else v/=aspect;}
                    if(mode==PresentationMode.FILL){if(aspect>1)v/=aspect;else u*=aspect;}
                    boolean bar=Math.abs(u)>0.5||Math.abs(v)>0.5;
                    double sx=Math.max(0,Math.min(sw-1,(u+0.5)*sw-0.5));
                    double sy=Math.max(0,Math.min(sh-1,(v+0.5)*sh-0.5));
                    int[] expected=bar?new int[]{0,0,0}:new int[]{(int)Math.round(10+20*sx+1e-9),(int)Math.round(20+30*sy+1e-9),(int)Math.round(5+10*sx+15*sy+1e-9)};
                    int i=(y*w+x)*4;
                    for(int c=0;c<3;c++)assertEquals(expected[c],actual[i+c]&255,mode+" "+w+"x"+h+" cell "+x+","+y+" channel "+c);
                    assertEquals(255,actual[i+3]&255);
                }
            }
        }
    }

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
