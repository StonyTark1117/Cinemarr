package stonytark.cinemarr.core.server;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Bounded round-robin egress queue with atomic batch admission. */
public final class FairEgressScheduler<K, P, M> {
    private final int maxItemsPerKey;
    private final int maxItemsPerGroup;
    private final int maxItems;
    private final long maxBytes;
    private final Map<K, QueueState<P, M>> queues = new LinkedHashMap<K, QueueState<P, M>>();
    private final Deque<K> active = new ArrayDeque<K>();
    private final Map<Object, Integer> groupItems = new LinkedHashMap<Object, Integer>();
    private int backlogItems;
    private long backlogBytes;
    private long rejectedBatches;

    public FairEgressScheduler(int maxItemsPerKey, int maxItems, long maxBytes) {
        this(maxItemsPerKey, maxItemsPerKey, maxItems, maxBytes);
    }

    public FairEgressScheduler(int maxItemsPerKey, int maxItemsPerGroup, int maxItems, long maxBytes) {
        if (maxItemsPerKey <= 0 || maxItemsPerGroup <= 0 || maxItems <= 0 || maxBytes <= 0L) throw new IllegalArgumentException("egress limits");
        this.maxItemsPerKey = maxItemsPerKey; this.maxItemsPerGroup=maxItemsPerGroup;this.maxItems = maxItems; this.maxBytes = maxBytes;
    }

    public synchronized boolean enqueueBatch(K key, P player, List<Item<M>> batch) {
        return enqueueBatch(key,key,player,batch);
    }

    public synchronized boolean enqueueBatch(K key, Object group, P player, List<Item<M>> batch) {
        if (key == null || group == null || player == null || batch == null) throw new IllegalArgumentException("egress batch");
        if (batch.isEmpty()) return true;
        long batchBytes = 0L;
        for (Item<M> item : batch) {
            if (item == null) throw new IllegalArgumentException("egress item");
            batchBytes = saturatedAdd(batchBytes, item.sizeBytes());
        }
        QueueState<P, M> queue = queues.get(key);
        int keyItems = queue == null ? 0 : queue.items.size();
        int groupedItems=groupItems.containsKey(group)?groupItems.get(group):0;
        if (batch.size() > maxItemsPerKey - keyItems || batch.size()>maxItemsPerGroup-groupedItems || batch.size() > maxItems - backlogItems
                || batchBytes > maxBytes - backlogBytes) {
            rejectedBatches++;
            return false;
        }
        if (queue == null) { queue = new QueueState<P, M>(player); queues.put(key, queue); active.addLast(key); }
        else queue.player = player;
        for(Item<M> item:batch)queue.items.addLast(new QueuedItem<M>(group,item));
        groupItems.put(group,groupedItems+batch.size());backlogItems += batch.size(); backlogBytes += batchBytes;
        return true;
    }

    public synchronized int drain(int maxDrainItems, long maxDrainBytes, Sender<P, M> sender) {
        if (maxDrainItems <= 0 || maxDrainBytes <= 0L || sender == null) return 0;
        int drained = 0; long drainedBytes = 0L;
        while (drained < maxDrainItems && !active.isEmpty()) {
            int cycle = active.size(); boolean madeProgress = false;
            for (int index = 0; index < cycle && drained < maxDrainItems; index++) {
                K key = active.removeFirst(); QueueState<P, M> queue = queues.get(key);
                if (queue == null || queue.items.isEmpty()) continue;
                QueuedItem<M> queued = queue.items.peekFirst();Item<M> item=queued.item;
                if (item.sizeBytes() <= maxDrainBytes - drainedBytes) {
                    queue.items.removeFirst();decrementGroup(queued.group); backlogItems--; backlogBytes -= item.sizeBytes();
                    sender.send(queue.player, item.message()); drained++; drainedBytes += item.sizeBytes(); madeProgress = true;
                }
                if (queue.items.isEmpty()) queues.remove(key); else active.addLast(key);
            }
            if (!madeProgress) break;
        }
        return drained;
    }

    public synchronized void remove(K key) {
        QueueState<P, M> queue = queues.remove(key); if (queue == null) return;
        active.remove(key); backlogItems -= queue.items.size();
        for (QueuedItem<M> queued : queue.items){backlogBytes -= queued.item.sizeBytes();decrementGroup(queued.group);}
    }
    public synchronized void clear() { queues.clear(); active.clear();groupItems.clear(); backlogItems = 0; backlogBytes = 0L; }
    public synchronized int backlogItems() { return backlogItems; }
    public synchronized long backlogBytes() { return backlogBytes; }
    public synchronized long rejectedBatches() { return rejectedBatches; }
    private void decrementGroup(Object group){int remaining=groupItems.get(group)-1;if(remaining==0)groupItems.remove(group);else groupItems.put(group,remaining);}
    private static long saturatedAdd(long left, long right) { return right > Long.MAX_VALUE - left ? Long.MAX_VALUE : left + right; }

    public interface Sender<P, M> { void send(P player, M message); }
    public static final class Item<M> {
        private final M message; private final int sizeBytes;
        public Item(M message, int sizeBytes) { if (message == null || sizeBytes < 0) throw new IllegalArgumentException("egress item"); this.message=message;this.sizeBytes=sizeBytes; }
        public M message() { return message; } public int sizeBytes() { return sizeBytes; }
    }
    private static final class QueueState<P, M> {
        private P player; private final Deque<QueuedItem<M>> items = new ArrayDeque<QueuedItem<M>>();
        private QueueState(P player) { this.player = player; }
    }
    private static final class QueuedItem<M>{private final Object group;private final Item<M> item;private QueuedItem(Object group,Item<M> item){this.group=group;this.item=item;}}
}
