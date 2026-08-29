package stonytark.cinemarr.core.server;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecretRedactorTest {
    @Test void redactsPlainAndEncodedTokens() {
        String secret = "a+b/c=";
        String redacted = SecretRedactor.redact("url?X-Plex-Token=a%2Bb%2Fc%3D and " + secret, secret);
        assertFalse(redacted.contains(secret)); assertFalse(redacted.contains("a%2Bb%2Fc%3D"));
        assertTrue(redacted.contains("<redacted>"));
    }

    @Test void redactsConfiguredPlexEndpointAndHostFromNestedFailures() {
        String endpoint = "http://PRIVATE-PLEX.example:32400";
        RuntimeException wrapped = new RuntimeException("outer",
                new java.net.UnknownHostException("private-plex.example"));
        String redacted = SecretRedactor.message(wrapped, "secret-token", endpoint);
        assertFalse(redacted.contains(endpoint));
        assertFalse(redacted.contains("private-plex.example"));
        assertTrue(redacted.contains("<redacted>"));
    }

    @Test void redactsUnexpectedHttpEndpointsEvenWithoutAConfiguredMatch() {
        String redacted = SecretRedactor.redact("request failed at https://other.example/path?value=1", "secret-token");
        assertFalse(redacted.contains("other.example"));
        assertTrue(redacted.contains("<redacted-endpoint>"));
    }
}
