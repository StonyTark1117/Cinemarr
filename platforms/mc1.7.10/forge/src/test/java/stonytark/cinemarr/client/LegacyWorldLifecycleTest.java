package stonytark.cinemarr.client;

import org.junit.jupiter.api.Test;
import stonytark.cinemarr.core.client.ClockSynchronizer;
import stonytark.cinemarr.core.server.BoundedWorkExecutor;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class LegacyWorldLifecycleTest {
    @Test void worldUnloadClosesMediaButRetainsConnectionNegotiationAndClock() throws Exception {
        LegacyClientState state = LegacyClientState.INSTANCE;
        state.stop();
        try {
            set(state,"helloSent",true); set(state,"helloConfirmed",true);
            set(state,"acceptanceVideoTuneSent",true); set(state,"acceptanceVideoPlaySent",true);
            set(state,"acceptanceVideoController",17L);
            ClockSynchronizer clock = (ClockSynchronizer) get(state,"clock");
            clock.accept(1000,1000,1000);
            LegacyVideoClientState visible = LegacyVideoClientState.INSTANCE;
            LegacyVideoClientState.StreamKey key = new LegacyVideoClientState.StreamKey(UUID.randomUUID(),4);
            map(visible,"streams").put(key,new LegacyVideoClientState.StreamState(key));
            LegacyVideoRuntime runtime = LegacyVideoRuntime.INSTANCE;
            LegacyVideoPlaybackManager playback = (LegacyVideoPlaybackManager) get(runtime,"playback");
            LegacyVideoPlayback pipeline = new LegacyVideoPlayback();
            BoundedWorkExecutor executor = (BoundedWorkExecutor) get(pipeline,"executor");
            executor.run(() -> {}).get(2,TimeUnit.SECONDS);
            map(playback,"pipelines").put(key,pipeline);
            LegacyVideoAudioManager audio = (LegacyVideoAudioManager) get(runtime,"audio");
            map(audio,"players").put(key,new LegacyVideoAudio());
            state.worldUnloaded();
            assertTrue(visible.streamStates().isEmpty()); assertTrue(visible.televisions().isEmpty());
            assertTrue(playback.pipelines().isEmpty()); assertEquals(0,audio.sourceCount());
            assertTrue(executor.isShutdown());
            assertEquals(true,get(state,"helloSent")); assertEquals(true,get(state,"helloConfirmed"));
            assertEquals(true,get(state,"acceptanceVideoTuneSent")); assertEquals(true,get(state,"acceptanceVideoPlaySent"));
            assertEquals(0L,get(state,"acceptanceVideoController")); assertEquals(1,clock.sampleCount());
            assertDoesNotThrow(state::worldUnloaded);
            state.stop(); assertEquals(false,get(state,"helloConfirmed")); assertEquals(0,clock.sampleCount());
        } finally { state.stop(); }
    }

    private static Object get(Object target,String name) throws Exception {
        Field field=target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target);
    }
    private static void set(Object target,String name,Object value) throws Exception {
        Field field=target.getClass().getDeclaredField(name); field.setAccessible(true); field.set(target,value);
    }
    @SuppressWarnings("unchecked") private static Map<Object,Object> map(Object target,String name) throws Exception {
        return (Map<Object,Object>) get(target,name);
    }
}
