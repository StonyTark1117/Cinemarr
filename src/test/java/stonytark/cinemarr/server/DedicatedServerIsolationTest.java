package stonytark.cinemarr.server;

import org.junit.jupiter.api.Test;
import stonytark.cinemarr.Cinemarr;
import stonytark.cinemarr.network.ClientPayloadBridge;
import stonytark.cinemarr.network.CinemarrNetwork;
import stonytark.cinemarr.network.CinemarrPayloads;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class DedicatedServerIsolationTest {
    @Test void commonAndServerClassesHaveNoClientOpenAlOrLwjglLinkage() throws Exception {
        List<Class<?>> classes = List.of(Cinemarr.class, CinemarrNetwork.class, CinemarrPayloads.class, ClientPayloadBridge.class,
                CinemarrServer.class, CinemarrCommands.class, ServerVideoManager.class,
                stonytark.cinemarr.core.server.PlexVideoService.class,
                stonytark.cinemarr.core.server.VideoSessionCoordinator.class);
        for (Class<?> type : classes) {
            assertDoesNotThrow(() -> Class.forName(type.getName(), false, type.getClassLoader()));
            String resource = "/" + type.getName().replace('.', '/') + ".class";
            try (InputStream input = type.getResourceAsStream(resource)) {
                assertNotNull(input, resource);
                String constants = new String(input.readAllBytes(), StandardCharsets.ISO_8859_1);
                assertFalse(constants.contains("net/minecraft/client"), type + " links client Minecraft classes");
                assertFalse(constants.contains("com/mojang/blaze3d"), type + " links OpenAL integration");
                assertFalse(constants.contains("org/lwjgl"), type + " links LWJGL");
            }
        }
    }
}
