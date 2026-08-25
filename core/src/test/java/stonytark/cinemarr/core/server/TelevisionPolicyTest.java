package stonytark.cinemarr.core.server;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TelevisionPolicyTest {
    @Test void limitsEachOwnerIndependentlyAndReleasesCapacity() {
        TelevisionPolicy policy = new TelevisionPolicy(2);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        assertTrue(policy.claim(first));
        assertTrue(policy.claim(first));
        assertFalse(policy.claim(first));
        assertTrue(policy.claim(second));
        policy.release(first);
        assertTrue(policy.claim(first));
        assertEquals(2, policy.ownedBy(first));
    }
}
