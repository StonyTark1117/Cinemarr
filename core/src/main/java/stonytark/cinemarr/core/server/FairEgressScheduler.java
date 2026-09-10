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
    private final long maxBytesPerKey;
    private final long maxBytesPerGroup;
    private final int maxItems;
    private final long maxBytes;
    private final Map<K, QueueState<P, M>> queues = new LinkedHashMap<K, QueueState<P, M>>();
    private final Deque<K> active = new ArrayDeque<K>();
    private final Map<Object, Integer> groupItems = new LinkedHashMap<Object, Integer>();
    private final Map<Object, Long> groupBytes = new LinkedHashMap<Object, Long>();
    private int backlogItems;
    private long backlogBytes;
    private long rejectedBatches;

    public FairEgressScheduler(int maxItemsPerKey, int maxItems, long maxBytes) {
        this(maxItemsPerKey, maxItemsPerKey, maxItems, maxBytes);
    }

    public FairEgressScheduler(int maxItemsPerKey, int maxItemsPerGroup, int maxItems, long maxBytes) {
        this(maxItemsPerKey, maxBytes, maxItemsPerGroup, maxBytes, maxItems, maxBytes);
    }

    public FairEgressScheduler(int maxItemsPerKey, long maxBytesPerKey,
                              int maxItemsPerGroup, long maxBytesPerGroup,
                              int maxItems, long maxBytes) {
        if (maxItemsPerKey <= 0 || maxItemsPerGroup <= 0 || maxItems <= 0
                || maxBytesPerKey <= 0 || maxBytesPerGroup <= 0 || maxBytes <= 0)
            throw new IllegalArgumentException("egress limits");
        this.maxItemsPerKey = maxItemsPerKey;
        this.maxBytesPerKey = maxBytesPerKey;
        this.maxItemsPerGroup = maxItemsPerGroup;
        this.maxBytesPerGroup = maxBytesPerGroup;
        this.maxItems = maxItems;
        this.maxBytes = maxBytes;
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
        long keyBytes = queue == null ? 0 : queue.bytes;
        int groupedItems=groupItems.containsKey(group)?groupItems.get(group):0;
        long groupedBytes = groupBytes.containsKey(group) ? groupBytes.get(group) : 0;
        if (batch.size() > maxItemsPerKey - keyItems || batch.size()>maxItemsPerGroup-groupedItems || batch.size() > maxItems - backlogItems
                || batchBytes > maxBytesPerKey - keyBytes || batchBytes > maxBytesPerGroup - groupedBytes
                || batchBytes > maxBytes - backlogBytes) {
            rejectedBatches++;
            return false;
        }
        if (queue == null) { queue = new QueueState<P, M>(player); queues.put(key, queue); active.addLast(key); }
        else queue.player = player;
        for(Item<M> item:batch)queue.items.addLast(new QueuedItem<M>(group,item));
        queue.bytes += batchBytes;
        groupBytes.put(group, groupedBytes + batchBytes);
        groupItems.put(group,groupedItems+batch.size());backlogItems += batch.size(); backlogBytes += batchBytes;
        return true;
    }

    public synchronized int drain(int maxDrainItems, long maxDrainBytes, Sender<P, M> sender) {
        if (maxDrainItems <= 0 || maxDrainBytes <= 0L || sender == null) return 0;
        int drained = 0; long drainedBytes = 0L;
        while (drained < maxDrainItems && !active.isEmpty()) {
            int cycle = active.size(); boolean madeProgress = false;
            // A synchronous send can disconnect several clients or clear every
            // queue. The cycle size is only a fairness bound, not proof that
            // the active ring still contains an entry after that callback.
            for (int index = 0; index < cycle && drained < maxDrainItems && !active.isEmpty(); index++) {
                K key = active.removeFirst(); QueueState<P, M> queue = queues.get(key);
                if (queue == null || queue.items.isEmpty()) continue;
                QueuedItem<M> queued = queue.items.peekFirst();Item<M> item=queued.item;
                if (item.sizeBytes() <= maxDrainBytes - drainedBytes) {
                    queue.items.removeFirst(); decrementGroup(queued.group, item.sizeBytes());
                    queue.bytes -= item.sizeBytes(); backlogItems--; backlogBytes -= item.sizeBytes();
                    try {
                        sender.send(queue.player, item.message());
                        drained++; drainedBytes += item.sizeBytes(); madeProgress = true;
                    } finally {
                        // A failed transport must not leave the remaining
                        // backlog detached from the round-robin ring. A sender
                        // can also remove/clear its queue during disconnect.
                        if (queues.get(key) == queue) {
                            if (queue.items.isEmpty()) queues.remove(key);
                            else active.addLast(key);
                        }
                    }
                } else {
                    active.addLast(key);
                }
            }
            if (!madeProgress) break;
        }
        return drained;
    }

    public synchronized void remove(K key) {
        QueueState<P, M> queue = queues.remove(key); if (queue == null) return;
        active.remove(key); backlogItems -= queue.items.size();
        for (QueuedItem<M> queued : queue.items){backlogBytes -= queued.item.sizeBytes();decrementGroup(queued.group, queued.item.sizeBytes());}
    }
    /** Remove one stream's queued messages without disturbing sibling order or budgets. */
    public synchronized int removeMatching(K key, java.util.function.Predicate<M> matches) {
        if (matches == null) throw new IllegalArgumentException("egress selector");
        QueueState<P, M> queue = queues.get(key); if (queue == null) return 0;
        int removed=0;
        java.util.Iterator<QueuedItem<M>> iterator=queue.items.iterator();
        while (iterator.hasNext()) {
            QueuedItem<M> queued=iterator.next();
            if (!matches.test(queued.item.message())) continue;
            iterator.remove(); removed++; backlogItems--;
            int bytes=queued.item.sizeBytes(); queue.bytes-=bytes; backlogBytes-=bytes;
            decrementGroup(queued.group, bytes);
        }
        if (queue.items.isEmpty()) { queues.remove(key); active.remove(key); }
        return removed;
    }
    public synchronized void clear() { queues.clear(); active.clear();groupItems.clear();groupBytes.clear(); backlogItems = 0; backlogBytes = 0L; }
    public synchronized int backlogItems() { return backlogItems; }
    public synchronized long backlogBytes() { return backlogBytes; }
    public synchronized long rejectedBatches() { return rejectedBatches; }
    /** Diagnostic only; connected viewers may legitimately retain queued transfers. */
    public synchronized int backlogItemsOutside(java.util.Set<K> connected) {
        int count = 0;
        for (Map.Entry<K, QueueState<P, M>> entry : queues.entrySet())
            if (!connected.contains(entry.getKey())) count += entry.getValue().items.size();
        return count;
    }
    public synchronized long backlogBytesOutside(java.util.Set<K> connected) {
        long bytes = 0;
        for (Map.Entry<K, QueueState<P, M>> entry : queues.entrySet())
            if (!connected.contains(entry.getKey())) bytes += entry.getValue().bytes;
        return bytes;
    }
    private void decrementGroup(Object group, int bytes) {
        int remaining = groupItems.get(group) - 1;
        if (remaining == 0) { groupItems.remove(group); groupBytes.remove(group); }
        else { groupItems.put(group, remaining); groupBytes.put(group, groupBytes.get(group) - bytes); }
    }
    private static long saturatedAdd(long left, long right) { return right > Long.MAX_VALUE - left ? Long.MAX_VALUE : left + right; }

    public interface Sender<P, M> { void send(P player, M message); }
    public static final class Item<M> {
        private final M message; private final int sizeBytes;
        public Item(M message, int sizeBytes) { if (message == null || sizeBytes < 0) throw new IllegalArgumentException("egress item"); this.message=message;this.sizeBytes=sizeBytes; }
        public M message() { return message; } public int sizeBytes() { return sizeBytes; }
    }
    private static final class QueueState<P, M> {
        private P player; private final Deque<QueuedItem<M>> items = new ArrayDeque<QueuedItem<M>>();
        private long bytes;
        private QueueState(P player) { this.player = player; }
    }
    private static final class QueuedItem<M>{private final Object group;private final Item<M> item;private QueuedItem(Object group,Item<M> item){this.group=group;this.item=item;}}
}
