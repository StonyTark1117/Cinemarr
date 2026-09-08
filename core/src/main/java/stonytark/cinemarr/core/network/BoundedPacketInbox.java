package stonytark.cinemarr.core.network;

import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/** Thread-safe, byte/item-bounded handoff with round-robin peer draining. */
public final class BoundedPacketInbox<K, V> {
    private final int maximumItems, maximumPeerItems;
    private final long maximumBytes, maximumPeerBytes;
    private final LinkedHashMap<K, Peer<V>> peers = new LinkedHashMap<>();
    private int items;
    private long bytes, rejected;

    public BoundedPacketInbox(int maximumItems, long maximumBytes,
                              int maximumPeerItems, long maximumPeerBytes) {
        if (maximumItems <= 0 || maximumBytes <= 0 || maximumPeerItems <= 0 || maximumPeerBytes <= 0
                || maximumPeerItems > maximumItems || maximumPeerBytes > maximumBytes)
            throw new IllegalArgumentException("inbox limits");
        this.maximumItems = maximumItems; this.maximumBytes = maximumBytes;
        this.maximumPeerItems = maximumPeerItems; this.maximumPeerBytes = maximumPeerBytes;
    }

    public synchronized boolean offer(K owner, V value, long payloadBytes) {
        if (owner == null || value == null || payloadBytes < 0) throw new IllegalArgumentException("packet");
        Peer<V> peer = peers.get(owner);
        if (items >= maximumItems || payloadBytes > maximumBytes - bytes
                || payloadBytes > maximumPeerBytes - (peer == null ? 0 : peer.bytes)
                || (peer != null && peer.queue.size() >= maximumPeerItems)) {
            rejected++;
            return false;
        }
        if (peer == null) { peer = new Peer<>(); peers.put(owner, peer); }
        peer.queue.addLast(new Packet<>(value, payloadBytes)); peer.bytes += payloadBytes;
        items++; bytes += payloadBytes;
        return true;
    }

    private synchronized V poll() {
        if (peers.isEmpty()) return null;
        Map.Entry<K, Peer<V>> first = peers.entrySet().iterator().next();
        K owner = first.getKey(); Peer<V> peer = first.getValue();
        peers.remove(owner);
        Packet<V> packet = peer.queue.removeFirst();
        items--; bytes -= packet.bytes; peer.bytes -= packet.bytes;
        if (!peer.queue.isEmpty()) peers.put(owner, peer);
        return packet.value;
    }

    /** Callbacks run outside the queue monitor; teardown/reentrant offers are safe. */
    public int drain(int maximum, Consumer<V> receiver) {
        if (maximum < 0 || receiver == null) throw new IllegalArgumentException("drain");
        int drained = 0;
        while (drained < maximum) {
            V value = poll(); if (value == null) break;
            drained++; receiver.accept(value);
        }
        return drained;
    }

    public synchronized void remove(K owner) {
        Peer<V> peer = peers.remove(owner);
        if (peer != null) { items -= peer.queue.size(); bytes -= peer.bytes; }
    }
    public synchronized void clear() { peers.clear(); items = 0; bytes = 0; }
    public synchronized int size() { return items; }
    public synchronized long retainedBytes() { return bytes; }
    public synchronized int owners() { return peers.size(); }
    public synchronized long rejectedPackets() { return rejected; }

    private static final class Peer<V> {
        final ArrayDeque<Packet<V>> queue = new ArrayDeque<>();
        long bytes;
    }
    private static final class Packet<V> {
        final V value; final long bytes;
        Packet(V value, long bytes) { this.value = value; this.bytes = bytes; }
    }
}
