package stonytark.cinemarr.core.screen;

import org.junit.jupiter.api.Test;

import java.util.BitSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScreenMaskMesherTest {
    @Test void sparseAndLShapedMeshesCoverExactlyTheirVisibleCellsWithoutOverlap() {
        for(int[] size:new int[][]{{4,4},{16,9},{17,11},{2000,20},{1,2048},{2048,1}}) {
            int w=size[0],h=size[1];
            for(boolean lShape:new boolean[]{false,true}) {
                BitSet expected=new BitSet(w*h);
                for(int y=0;y<h;y++)for(int x=0;x<w;x++)if(lShape?(x==0||y==h-1):((x+y)%3!=1))expected.set(y*w+x);
                BitSet covered=new BitSet(w*h);
                for(ScreenMaskMesher.Rectangle r:ScreenMaskMesher.mesh(w,h,expected.toByteArray()))
                    for(int y=r.y();y<r.y()+r.height();y++)for(int x=r.x();x<r.x()+r.width();x++) {
                        org.junit.jupiter.api.Assertions.assertTrue(x>=0&&x<w&&y>=0&&y<h);
                        org.junit.jupiter.api.Assertions.assertFalse(covered.get(y*w+x));covered.set(y*w+x);
                    }
                assertEquals(expected,covered);
            }
        }
    }

    @Test void giantSolidScreenIsOneQuad() {
        BitSet mask = new BitSet(2000 * 20); mask.set(0, 2000 * 20);
        List<ScreenMaskMesher.Rectangle> values = ScreenMaskMesher.mesh(2000,20,mask.toByteArray());
        assertEquals(1,values.size()); assertEquals(2000,values.get(0).width()); assertEquals(20,values.get(0).height());
    }

    @Test void holesRemainHolesWhileEqualRunsMergeVertically() {
        BitSet mask = new BitSet(12);
        mask.set(0);mask.set(1);mask.set(4);mask.set(5);mask.set(8);mask.set(10);
        List<ScreenMaskMesher.Rectangle> values=ScreenMaskMesher.mesh(4,3,mask.toByteArray());
        assertEquals(3,values.size());
        assertEquals(6,values.stream().mapToInt(value->value.width()*value.height()).sum());
        assertEquals(6,mask.cardinality());
    }
}
