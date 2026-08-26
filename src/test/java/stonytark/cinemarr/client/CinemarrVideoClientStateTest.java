package stonytark.cinemarr.client;

import org.junit.jupiter.api.Test;
import stonytark.cinemarr.core.library.MediaKind;
import stonytark.cinemarr.core.library.VideoMediaItem;
import stonytark.cinemarr.core.protocol.VideoPackets;
import stonytark.cinemarr.core.screen.ScreenFacing;
import stonytark.cinemarr.core.video.PresentationMode;
import stonytark.cinemarr.network.VideoPayloads;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CinemarrVideoClientStateTest {
    @Test void multipleTelevisionsShareOneWatchPartyStreamButIndependentSessionsDecodeSeparately() {
        CinemarrVideoClientState state=CinemarrVideoClientState.INSTANCE;state.reset();
        UUID party=UUID.randomUUID(),independent=UUID.randomUUID();
        state.accept(new VideoPayloads.SessionState(session(1,UUID.randomUUID(),party,4,true)));
        state.accept(new VideoPayloads.SessionState(session(2,UUID.randomUUID(),party,4,true)));
        state.accept(new VideoPayloads.SessionState(session(3,UUID.randomUUID(),independent,7,true)));
        assertEquals(3,state.televisions().size());assertEquals(2,state.streamStates().size());assertNotNull(state.session(2));
        state.accept(new VideoPayloads.SessionState(session(1,UUID.randomUUID(),new UUID(0,0),0,false)));
        assertEquals(2,state.streamStates().size());
        state.accept(new VideoPayloads.SessionState(session(2,UUID.randomUUID(),new UUID(0,0),0,false)));
        assertEquals(1,state.streamStates().size());
        state.accept(new VideoPayloads.TelevisionRemoved(new VideoPackets.TelevisionRemoved(3)));
        assertEquals(0,state.streamStates().size());assertEquals(2,state.televisions().size());state.reset();
    }

    private static VideoPackets.SessionState session(long controller,UUID tv,UUID session,long generation,boolean playing){
        return new VideoPackets.SessionState(tv,controller,session,generation,playing?VideoPackets.SessionStatus.PLAYING:VideoPackets.SessionStatus.IDLE,
                playing?new VideoMediaItem(MediaKind.MOVIE,"1","Movie","","PG",0,60_000):null,0,60_000,false,PresentationMode.FIT,
                4,4,new byte[]{(byte)255,(byte)255},ScreenFacing.NORTH,0,0,0,java.util.List.of(),-1,-1,System.currentTimeMillis(),true,"");
    }
}
