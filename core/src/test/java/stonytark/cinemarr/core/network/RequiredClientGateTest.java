package stonytark.cinemarr.core.network;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class RequiredClientGateTest {
    @AfterEach void reset(){RequiredClientGate.clear();}
    @Test void rejectsPrematureAndDuplicateHello(){UUID id=UUID.randomUUID();assertFalse(RequiredClientGate.accept(id));RequiredClientGate.require(id,10);assertTrue(RequiredClientGate.accept(id));assertTrue(RequiredClientGate.accepted(id));assertFalse(RequiredClientGate.accept(id));}
    @Test void timeoutAndDisconnectClearOwnership(){UUID id=UUID.randomUUID();RequiredClientGate.require(id,100);long deadline=100+stonytark.cinemarr.core.protocol.ProtocolLimits.CLIENT_HELLO_TIMEOUT_MS;assertTrue(RequiredClientGate.expire(deadline-1).isEmpty());assertEquals(java.util.Collections.singletonList(id),RequiredClientGate.expire(deadline));RequiredClientGate.require(id,deadline+1);assertTrue(RequiredClientGate.accept(id));RequiredClientGate.remove(id);assertFalse(RequiredClientGate.accepted(id));}
}
