package stonytark.cinemarr.core.server;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class FairEgressSchedulerTest {
    @Test void orphanAccountingDistinguishesDepartedWorkFromContinuingViewers() {
        FairEgressScheduler<String,String,String> scheduler = new FairEgressScheduler<>(8,16,1024);
        scheduler.enqueueBatch("active", "A", items("one", "two"));
        scheduler.enqueueBatch("departed", "D", items("three"));
        assertEquals(1, scheduler.backlogItemsOutside(Collections.singleton("active")));
        assertEquals(2, scheduler.backlogBytesOutside(Collections.singleton("active")));
        scheduler.remove("departed");
        assertEquals(0, scheduler.backlogItemsOutside(Collections.singleton("active")));
        assertEquals(0, scheduler.backlogBytesOutside(Collections.singleton("active")));
        assertEquals(2, scheduler.backlogItems());
        scheduler.enqueueBatch("departed", "D", items("late"));
        assertEquals(1, scheduler.backlogItemsOutside(Collections.singleton("active")));
        scheduler.drain(16,1024,(p,m)->{});
        assertEquals(0, scheduler.backlogBytesOutside(Collections.<String>emptySet()));
    }

    @Test void independentByteBudgetsRejectWholeBatchesAndReleaseOnDrainRemoveAndClear() {
        FairEgressScheduler<String,String,String> scheduler = new FairEgressScheduler<>(20,6,40,10,100,16);
        List<FairEgressScheduler.Item<String>> six = Collections.singletonList(new FairEgressScheduler.Item<>("six",6));
        List<FairEgressScheduler.Item<String>> four = Collections.singletonList(new FairEgressScheduler.Item<>("four",4));
        assertTrue(scheduler.enqueueBatch("a","session","A",six));
        assertFalse(scheduler.enqueueBatch("a","other","A",four), "client bytes aggregate across sessions");
        assertTrue(scheduler.enqueueBatch("b","session","B",four));
        assertFalse(scheduler.enqueueBatch("c","session","C",items("two")), "session bytes aggregate across clients");
        assertTrue(scheduler.enqueueBatch("c","other","C",six));
        assertFalse(scheduler.enqueueBatch("d","third","D",items("two")), "global byte budget");
        assertEquals(16,scheduler.backlogBytes());
        assertEquals(3,scheduler.rejectedBatches());
        assertEquals(1,scheduler.drain(1,6,(p,m)->{}));
        assertTrue(scheduler.enqueueBatch("a","session","A",six), "drain releases client and session bytes");
        scheduler.remove("b");
        assertTrue(scheduler.enqueueBatch("b","session","B",four), "disconnect releases session bytes");
        scheduler.clear();
        assertEquals(0,scheduler.backlogItems()); assertEquals(0,scheduler.backlogBytes());
        assertTrue(scheduler.enqueueBatch("a","session","A",six));
        assertThrows(IllegalArgumentException.class, () -> new FairEgressScheduler<>(1,0L,1,1L,1,1L));
        assertThrows(IllegalArgumentException.class, () -> new FairEgressScheduler<>(1,1L,1,0L,1,1L));
    }

    @Test void continuouslyRefillingGreedyClientCannotStarveOtherClients() {
        FairEgressScheduler<String,String,String> scheduler = new FairEgressScheduler<>(8,32,32,96,64,192);
        int rejected = 0;
        for (int tick=0; tick<1000; tick++) {
            scheduler.enqueueBatch("greedy","g","G",items("g","g","g","g"));
            if (!scheduler.enqueueBatch("greedy","g","G",items("g","g","g","g","g","g","g","g"))) rejected++;
            assertTrue(scheduler.enqueueBatch("quiet","q","Q",items("quiet")));
            List<String> sent = new ArrayList<>();
            scheduler.drain(3,6,(p,m)->sent.add(m));
            assertTrue(sent.contains("quiet"), "quiet client delivered within one bounded tick");
            assertTrue(scheduler.backlogItems() <= 8);
            assertEquals(scheduler.backlogItems()*2L,scheduler.backlogBytes());
        }
        assertTrue(rejected > 0);
        scheduler.clear(); assertEquals(0,scheduler.backlogBytes());
    }

    @Test void sendExceptionCannotStrandRemainingItemsOutsideTheActiveRing() {
        FairEgressScheduler<String,String,String> scheduler = new FairEgressScheduler<>(8,16,1024);
        scheduler.enqueueBatch("a", "A", items("first", "second"));
        scheduler.enqueueBatch("b", "B", items("other"));
        assertThrows(IllegalStateException.class, () -> scheduler.drain(8,1024,
                (player,message) -> { throw new IllegalStateException("disconnected transport"); }));
        List<String> sent = new ArrayList<>();
        assertEquals(2, scheduler.drain(8,1024,(player,message) -> sent.add(message)));
        assertEquals(Arrays.asList("other", "second"), sent);
        assertEquals(0, scheduler.backlogItems());
        assertEquals(0, scheduler.backlogBytes());
    }

    @Test void reentrantDisconnectDuringSendCannotResurrectTheRemovedQueue() {
        FairEgressScheduler<String,String,String> scheduler = new FairEgressScheduler<>(8,16,1024);
        scheduler.enqueueBatch("a", "A", items("first", "second"));
        assertEquals(1, scheduler.drain(8,1024,(player,message) -> scheduler.remove("a")));
        assertEquals(0, scheduler.backlogItems());
        assertEquals(0, scheduler.backlogBytes());
        scheduler.enqueueBatch("a", "A", items("fresh"));
        assertEquals(1, scheduler.drain(8,1024,(player,message) -> {}));
    }

    @Test void reentrantClearWithMultipleActiveClientsEndsTheCurrentCycle() {
        FairEgressScheduler<String,String,String> scheduler = new FairEgressScheduler<>(8,16,1024);
        scheduler.enqueueBatch("a", "A", items("first", "discard-a"));
        scheduler.enqueueBatch("b", "B", items("discard-b"));
        assertEquals(1, scheduler.drain(8,1024,(player,message) -> scheduler.clear()));
        assertEquals(0, scheduler.backlogItems());
        assertEquals(0, scheduler.backlogBytes());
        scheduler.enqueueBatch("b", "B", items("fresh"));
        List<String> sent = new ArrayList<>();
        assertEquals(1, scheduler.drain(8,1024,(player,message) -> sent.add(message)));
        assertEquals(Collections.singletonList("fresh"), sent);
    }

    @Test void reentrantDisconnectOfAllClientsDoesNotUseTheOldCycleSize() {
        FairEgressScheduler<String,String,String> scheduler = new FairEgressScheduler<>(8,16,1024);
        scheduler.enqueueBatch("a", "A", items("first", "discard-a"));
        scheduler.enqueueBatch("b", "B", items("discard-b"));
        scheduler.enqueueBatch("c", "C", items("discard-c"));
        assertEquals(1, scheduler.drain(8,1024,(player,message) -> {
            scheduler.remove("a"); scheduler.remove("b"); scheduler.remove("c");
        }));
        assertEquals(0, scheduler.backlogItems());
        assertEquals(0, scheduler.backlogBytes());
    }

    @Test void reentrantClearAndReplacementCannotRestoreTheOldQueue() {
        FairEgressScheduler<String,String,String> scheduler = new FairEgressScheduler<>(8,16,1024);
        scheduler.enqueueBatch("a", "old-A", items("first", "discard-a"));
        scheduler.enqueueBatch("b", "old-B", items("discard-b"));
        List<String> sent = new ArrayList<>();
        assertEquals(2, scheduler.drain(8,1024,(player,message) -> {
            sent.add(player + ":" + message);
            if ("first".equals(message)) {
                scheduler.clear();
                assertTrue(scheduler.enqueueBatch("a", "new-A", items("replacement")));
            }
        }));
        assertEquals(Arrays.asList("old-A:first", "new-A:replacement"), sent);
        assertEquals(0, scheduler.backlogItems());
        assertEquals(0, scheduler.backlogBytes());
    }

    @Test void drainsRoundRobinAndKeepsAccountingExact() {
        FairEgressScheduler<String,String,String> scheduler=new FairEgressScheduler<String,String,String>(8,16,1024);
        scheduler.enqueueBatch("a","A",items("A1","A2","A3"));scheduler.enqueueBatch("b","B",items("B1","B2"));scheduler.enqueueBatch("c","C",items("C1"));List<String> sent=new ArrayList<String>();
        assertEquals(6,scheduler.drain(16,1024,(player,message)->sent.add(message)));assertEquals(Arrays.asList("A1","B1","C1","A2","B2","A3"),sent);assertEquals(0,scheduler.backlogItems());assertEquals(0,scheduler.backlogBytes());
    }
    @Test void rejectsWholeOverloadBatchAndDisconnectDropsOnlyThatKey() {
        FairEgressScheduler<String,String,String> scheduler=new FairEgressScheduler<String,String,String>(2,3,12);
        assertTrue(scheduler.enqueueBatch("a","A",items("A1","A2")));assertFalse(scheduler.enqueueBatch("a","A",items("A3")));assertFalse(scheduler.enqueueBatch("b","B",items("B1","B2")));assertEquals(2,scheduler.backlogItems());assertEquals(2,scheduler.rejectedBatches());
        scheduler.remove("a");assertEquals(0,scheduler.backlogItems());assertEquals(0,scheduler.backlogBytes());
    }
    @Test void byteBudgetCanSkipAnOversizedHeadWithoutStarvingAnotherKey() {
        FairEgressScheduler<String,String,String> scheduler=new FairEgressScheduler<String,String,String>(8,16,1024);scheduler.enqueueBatch("a","A",Collections.singletonList(new FairEgressScheduler.Item<String>("large",9)));scheduler.enqueueBatch("b","B",Collections.singletonList(new FairEgressScheduler.Item<String>("small",4)));List<String> sent=new ArrayList<String>();
        assertEquals(1,scheduler.drain(8,5,(player,message)->sent.add(message)));assertEquals(Collections.singletonList("small"),sent);assertEquals(1,scheduler.backlogItems());
    }
    @Test void sessionLimitAppliesAcrossClientsAndReleasesOnDrain(){
        FairEgressScheduler<String,String,String> scheduler=new FairEgressScheduler<String,String,String>(4,3,12,1024);List<FairEgressScheduler.Item<String>> two=items("1","2");
        assertTrue(scheduler.enqueueBatch("client-a","session","A",two));assertFalse(scheduler.enqueueBatch("client-b","session","B",two));
        assertEquals(2,scheduler.drain(2,1024,(player,message)->{}));assertTrue(scheduler.enqueueBatch("client-b","session","B",two));
    }
    private static List<FairEgressScheduler.Item<String>> items(String... values){List<FairEgressScheduler.Item<String>> result=new ArrayList<FairEgressScheduler.Item<String>>();for(String value:values)result.add(new FairEgressScheduler.Item<String>(value,2));return result;}
}
