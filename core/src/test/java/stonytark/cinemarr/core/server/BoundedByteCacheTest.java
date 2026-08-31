package stonytark.cinemarr.core.server;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BoundedByteCacheTest {
    @Test void evictsByBytesAndRecentAccess(){BoundedByteCache<String,byte[]> cache=new BoundedByteCache<String,byte[]>(3,6,v->v.length);cache.put("a",new byte[2]);cache.put("b",new byte[2]);cache.get("a");cache.put("c",new byte[3]);assertNull(cache.get("b"));assertNotNull(cache.get("a"));assertEquals(5,cache.retainedBytes());}
    @Test void rejectsSingleOversizedValueWithoutRetainingIt(){BoundedByteCache<String,byte[]> cache=new BoundedByteCache<String,byte[]>(2,4,v->v.length);assertFalse(cache.put("large",new byte[5]));assertEquals(0,cache.size());assertEquals(0,cache.retainedBytes());}
}
