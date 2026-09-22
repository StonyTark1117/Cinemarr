package stonytark.cinemarr.core.video;

import org.junit.jupiter.api.Test;
import stonytark.cinemarr.core.protocol.VideoPackets;
import stonytark.cinemarr.core.server.VideoSessionCoordinator;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class PresentationCommandGuardTest {
    @Test void staleOrRetuningPresentationCannotChangeAttachment() throws Exception {
        try(VideoSessionCoordinator coordinator=new VideoSessionCoordinator(2,30000,(id,g,item,offset)->()->{})) {
            UUID tv=UUID.randomUUID();
            VideoSessionCoordinator.Snapshot existing=coordinator.tune(tv,"existing");
            for(VideoPackets.SessionCommand command:new VideoPackets.SessionCommand[]{command("other",existing.generation()),command("",existing.generation()+1)}) {
                assertThrows(IllegalStateException.class,()->{
                    PresentationCommandGuard.validate(command,"existing",existing.generation());
                    coordinator.tune(tv,command.sessionName());
                });
                assertTrue(coordinator.snapshot("existing",0).televisions().contains(tv));
                assertNull(coordinator.snapshotIfPresent("other",0));
            }
            assertDoesNotThrow(()->PresentationCommandGuard.validate(command("",existing.generation()),"existing",existing.generation()));
        }
    }
    private VideoPackets.SessionCommand command(String name,long generation) {
        return new VideoPackets.SessionCommand(VideoPackets.SessionAction.SET_PRESENTATION,1,"","",name,PresentationMode.FILL,generation,0,-1,-1);
    }
}
