package stonytark.cinemarr.core.screen;

import org.junit.jupiter.api.Test;

import java.util.BitSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScreenMaskMesherTest {
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
