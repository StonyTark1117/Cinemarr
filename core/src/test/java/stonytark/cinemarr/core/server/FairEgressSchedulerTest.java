package stonytark.cinemarr.core.server;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class FairEgressSchedulerTest {
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
