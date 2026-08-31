package stonytark.cinemarr.core.network;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class RequiredClientGateTest {
    @AfterEach void reset(){RequiredClientGate.clear();}
    @Test void rejectsPrematureAndDuplicateHello(){UUID id=UUID.randomUUID();assertFalse(RequiredClientGate.accept(id));RequiredClientGate.require(id,10);assertTrue(RequiredClientGate.accept(id));assertTrue(RequiredClientGate.accepted(id));assertFalse(RequiredClientGate.accept(id));}
    @Test void timeoutAndDisconnectClearOwnership(){UUID id=UUID.randomUUID();RequiredClientGate.require(id,100);assertTrue(RequiredClientGate.expire(5_099).isEmpty());assertEquals(java.util.Collections.singletonList(id),RequiredClientGate.expire(5_100));RequiredClientGate.require(id,10_000);assertTrue(RequiredClientGate.accept(id));RequiredClientGate.remove(id);assertFalse(RequiredClientGate.accepted(id));}
}
