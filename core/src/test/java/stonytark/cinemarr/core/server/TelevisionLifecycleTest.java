package stonytark.cinemarr.core.server;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class TelevisionLifecycleTest {
    @Test void countsOwnersGloballyAndNotifiesOnlyOnceOnRemoval() {
        AtomicInteger removals = new AtomicInteger();
        TelevisionLifecycle.reset((id, session) -> removals.incrementAndGet());
        UUID owner = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        assertTrue(TelevisionLifecycle.register(first, owner, 2));
        assertTrue(TelevisionLifecycle.register(second, owner, 2));
        assertFalse(TelevisionLifecycle.register(UUID.randomUUID(), owner, 2));
        assertEquals(2, TelevisionLifecycle.count(owner));
        TelevisionLifecycle.unregister(first, "party");
        TelevisionLifecycle.unregister(first, "party");
        assertEquals(1, TelevisionLifecycle.count(owner));
        assertEquals(1, removals.get());
        TelevisionLifecycle.reset(null);
    }

    @Test void indexesLocationsAcrossDimensionsRejectsOverlapAndOwnsRemoteRemoval() {
        TelevisionLifecycle.reset(null);
        UUID owner=UUID.randomUUID(),first=UUID.randomUUID(),second=UUID.randomUUID();
        AtomicInteger localRemovals=new AtomicInteger();
        assertTrue(TelevisionLifecycle.register(registration(first,owner,"overworld",1,10,localRemovals),8));
        assertTrue(TelevisionLifecycle.register(registration(second,owner,"the_nether",1,10,localRemovals),8));
        assertFalse(TelevisionLifecycle.register(registration(UUID.randomUUID(),owner,"overworld",2,10,localRemovals),8));
        List<TelevisionLifecycle.Registration> values=TelevisionLifecycle.registrations();
        assertEquals(2,values.size());
        assertEquals("overworld",TelevisionLifecycle.registration(first).dimension());
        TelevisionLifecycle.session(first,"family");
        TelevisionLifecycle.attachment(first,true);
        TelevisionLifecycle.validation(first,TelevisionLifecycle.Validation.LOADED);
        TelevisionLifecycle.Registration updated=TelevisionLifecycle.registration(first);
        assertEquals("family",updated.sessionName());assertTrue(updated.attached());
        assertEquals(TelevisionLifecycle.Validation.LOADED,updated.validation());
        assertEquals(1,TelevisionLifecycle.attachedSessionCount());
        assertTrue(TelevisionLifecycle.unregister(first));
        assertEquals(1,localRemovals.get());assertEquals(1,TelevisionLifecycle.count(owner));
        TelevisionLifecycle.reset(null);
    }

    @Test void restoresExistingRegistrationsAboveALoweredCapButRejectsDuplicateIds() {
        TelevisionLifecycle.reset(null);UUID owner=UUID.randomUUID();
        assertTrue(TelevisionLifecycle.restore(registration(UUID.randomUUID(),owner,"overworld",1,1,new AtomicInteger())));
        assertTrue(TelevisionLifecycle.restore(registration(UUID.randomUUID(),owner,"the_nether",1,1,new AtomicInteger())));
        assertEquals(2,TelevisionLifecycle.count(owner));
        UUID duplicate=UUID.randomUUID();
        assertTrue(TelevisionLifecycle.restore(registration(duplicate,owner,"the_end",3,20,new AtomicInteger())));
        assertFalse(TelevisionLifecycle.restore(registration(duplicate,owner,"overworld",30,40,new AtomicInteger())));
        assertFalse(TelevisionLifecycle.register(registration(UUID.randomUUID(),owner,"other",50,60,new AtomicInteger()),3));
        TelevisionLifecycle.reset(null);
    }

    private static TelevisionLifecycle.Registration registration(UUID id,UUID owner,String dimension,long first,long second,AtomicInteger removals){
        HashSet<Long> pixels=new HashSet<>();pixels.add(first);pixels.add(second);
        return new TelevisionLifecycle.Registration(id,owner,dimension,first,(int)first,64,(int)second,pixels,"",TelevisionLifecycle.Validation.SAVED,removals::incrementAndGet);
    }
}
