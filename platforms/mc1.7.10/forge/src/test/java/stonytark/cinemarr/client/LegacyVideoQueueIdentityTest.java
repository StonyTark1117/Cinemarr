package stonytark.cinemarr.client;

import org.junit.jupiter.api.Test;
import stonytark.cinemarr.core.library.MediaKind;
import stonytark.cinemarr.core.library.VideoMediaItem;
import stonytark.cinemarr.core.library.QueuedVideo;
import stonytark.cinemarr.core.protocol.VideoPackets;
import stonytark.cinemarr.core.screen.ScreenFacing;
import stonytark.cinemarr.core.video.PresentationMode;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyVideoQueueIdentityTest {
    @Test void independentTvStreamsRetainTheSharedQueueAcrossReplacementAndRemoval() throws Exception {
        LegacyVideoClientState state = LegacyVideoClientState.INSTANCE;
        state.reset();
        UUID party = UUID.randomUUID(), firstTv = UUID.randomUUID(), secondTv = UUID.randomUUID();
        UUID firstStream = UUID.randomUUID(), secondStream = UUID.randomUUID();
        try {
            acceptSession(state, session(1, firstTv, firstStream, 4, true).withTimeline(party, 12));
            acceptSession(state, session(2, secondTv, secondStream, 7, true).withTimeline(party, 12));
            queues(state).put(party,
                    java.util.Collections.singletonList(new QueuedVideo("movies", new VideoMediaItem(MediaKind.MOVIE, "2", "Queued", "", "PG", 0, 60_000))));
            assertEquals("Queued", state.queue(1).get(0).item().title());
            assertEquals("Queued", state.queue(2).get(0).item().title());
            assertEquals(2, state.streamStates().size());
            LegacyVideoClientState.StreamState sibling = state.stream(new LegacyVideoClientState.StreamKey(new stonytark.cinemarr.core.protocol.VideoStreamIdentity(party, 12, secondStream, 7)));
            acceptSession(state, session(1, firstTv, firstStream, 5, true).withTimeline(party, 12));
            assertEquals(2, state.streamStates().size());
            assertTrue(sibling != null);
            assertTrue(sibling == state.stream(new LegacyVideoClientState.StreamKey(new stonytark.cinemarr.core.protocol.VideoStreamIdentity(party, 12, secondStream, 7))));
            assertEquals("Queued", state.queue(1).get(0).item().title());
            removeTelevision(state, 1);
            assertEquals("Queued", state.queue(2).get(0).item().title());
            assertEquals(1, state.streamStates().size());
            // A paused or capacity-waiting TV retains its shared queue without owning media.
            acceptSession(state, session(2, secondTv, new UUID(0, 0), 0, false).withTimeline(party, 12));
            assertEquals("Queued", state.queue(2).get(0).item().title());
            assertEquals(0, state.streamStates().size());
            removeTelevision(state, 2);
            acceptSession(state, session(3, UUID.randomUUID(), UUID.randomUUID(), 1, true).withTimeline(party, 12));
            assertTrue(state.queue(3).isEmpty(), "the last removal must evict the cached party queue");
        } finally { state.reset(); }
    }

    @Test void sharedTimelineRevisionRetiresTheOldPipelineEvenIfStreamFieldsMatch() throws Exception {
        LegacyVideoClientState state = LegacyVideoClientState.INSTANCE; state.reset();
        UUID party = UUID.randomUUID(), tv = UUID.randomUUID(), stream = UUID.randomUUID();
        VideoPackets.SessionState before = session(1, tv, stream, 7, true).withTimeline(party, 41);
        VideoPackets.SessionState after = session(1, tv, stream, 7, true).withTimeline(party, 42);
        try {
            acceptSession(state, before);
            LegacyVideoClientState.StreamKey oldKey = new LegacyVideoClientState.StreamKey(before.identity());
            assertTrue(state.stream(oldKey) != null);
            acceptSession(state, after);
            assertEquals(1, state.streamStates().size());
            assertTrue(state.stream(oldKey) == null);
            assertTrue(state.stream(new LegacyVideoClientState.StreamKey(after.identity())) != null);
            assertEquals(0, state.televisionsForStream(oldKey).size());
        } finally { state.reset(); }
    }

    private static void acceptSession(LegacyVideoClientState state, VideoPackets.SessionState value) throws Exception {
        java.lang.reflect.Method method = LegacyVideoClientState.class.getDeclaredMethod("acceptSession", VideoPackets.SessionState.class);
        method.setAccessible(true); method.invoke(state, value);
    }
    private static void removeTelevision(LegacyVideoClientState state, long controller) throws Exception {
        java.lang.reflect.Method method = LegacyVideoClientState.class.getDeclaredMethod("removeTelevision", long.class);
        method.setAccessible(true); method.invoke(state, controller);
    }
    @SuppressWarnings("unchecked")
    private static java.util.Map<UUID, java.util.List<QueuedVideo>> queues(LegacyVideoClientState state) throws Exception {
        java.lang.reflect.Field field = LegacyVideoClientState.class.getDeclaredField("queues");
        field.setAccessible(true); return (java.util.Map<UUID, java.util.List<QueuedVideo>>) field.get(state);
    }

    private static VideoPackets.SessionState session(long controller,UUID tv,UUID session,long generation,boolean playing){
        return new VideoPackets.SessionState(tv,controller,session,generation,playing?VideoPackets.SessionStatus.PLAYING:VideoPackets.SessionStatus.IDLE,
                playing?new VideoMediaItem(MediaKind.MOVIE,"1","Movie","","PG",0,60_000):null,0,60_000,false,PresentationMode.FIT,
                4,4,new byte[]{(byte)255,(byte)255},ScreenFacing.NORTH,0,0,0,java.util.Collections.emptyList(),-1,-1,System.currentTimeMillis(),true,"");
    }
}
