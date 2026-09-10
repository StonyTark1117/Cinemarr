package stonytark.cinemarr.network;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import stonytark.cinemarr.core.client.ClientConnectionLifecycle;
import stonytark.cinemarr.core.protocol.CinemarrMessage;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClientPayloadBridgeTest {
    private record Message(String value) implements CinemarrMessage {}

    @AfterEach void clearReceiver() { ClientPayloadBridge.install(payload -> {}); }

    @Test void delayedOldPacketCannotMutateReplacementConnection() {
        List<CinemarrMessage> received = new ArrayList<>();
        Queue<Runnable> packets = new ArrayDeque<>();
        ClientConnectionLifecycle lifecycle = new ClientConnectionLifecycle(Runnable::run, received::clear);
        ClientPayloadBridge.install(lifecycle::isActive, received::add);
        Object old = new Object(), replacement = new Object();
        lifecycle.joined(old, () -> {});
        packets.add(() -> ClientPayloadBridge.accept(old, new Message("obsolete")));
        lifecycle.disconnected(old);
        lifecycle.joined(replacement, () -> {});
        Message current = new Message("current");
        ClientPayloadBridge.accept(replacement, current);
        packets.remove().run();
        assertEquals(List.of(current), received);
    }

    @Test void disconnectInvalidatesDeliveryBeforeQueuedCleanup() {
        List<CinemarrMessage> received = new ArrayList<>();
        Queue<Runnable> lifecycleTasks = new ArrayDeque<>();
        ClientConnectionLifecycle lifecycle = new ClientConnectionLifecycle(lifecycleTasks::add, received::clear);
        ClientPayloadBridge.install(lifecycle::isActive, received::add);
        Object connection = new Object();
        lifecycle.joined(connection, () -> {});
        lifecycleTasks.remove().run();
        Message current = new Message("before disconnect");
        ClientPayloadBridge.accept(connection, current);
        lifecycle.disconnected(connection);
        ClientPayloadBridge.accept(connection, new Message("after disconnect"));
        assertEquals(List.of(current), received);
        lifecycleTasks.remove().run();
        assertEquals(List.of(), received);
    }

    @Test void guardedReceiverRequiresOriginEvenForOtherwiseEligiblePacket() {
        List<CinemarrMessage> received = new ArrayList<>();
        ClientPayloadBridge.install(connection -> true, received::add);
        ClientPayloadBridge.accept(new Message("missing origin"));
        ClientPayloadBridge.accept(null, new Message("null origin"));
        assertEquals(List.of(), received);
    }

    @Test void adapterWithItsOwnOriginGuardCanDeliver() {
        List<CinemarrMessage> received = new ArrayList<>();
        ClientPayloadBridge.install(received::add);
        Message current = new Message("prevalidated");
        ClientPayloadBridge.accept(current);
        assertEquals(List.of(current), received);
    }
}
