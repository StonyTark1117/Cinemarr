# Migration

Back up the world and configuration before changing Minecraft versions or loaders.

1. Stop the server cleanly.
2. Install the exact Cinemarr 1.0.0 artifact for the destination Minecraft version and loader on the server and every client. Quilt uses the matching Fabric artifact plus upstream Fabric API.
3. Keep `world/serverconfig/cinemarr-server.toml`, `world/serverconfig/cinemarr-libraries.toml`, and the world's `cinemarr_screens` / video-session saved data. Keep each client's `config/cinemarr-client.toml` locally.
4. Start the server, inspect `/cinemarr status` and `/cinemarr diagnostics`, then test a controller before allowing normal use.

Protocol 10 intentionally removes the inherited global music queue, stations, MP3 streaming, and music UI. Old music-only configuration keys are ignored and dropped when the canonical server file is rewritten. Old music saved data is not used by Cinemarr 1.0. Television geometry, ownership, presentation, rendition, named-session, and video queue/checkpoint data remain the supported persistence surface.

Screen-data schema 3 includes unfinished Quick TV construction footprints. After an interrupted build, loaded generated pixels are rolled back; positions in unloaded chunks remain recorded until recovery can safely inspect those chunks.

When a canonical configuration is absent, Cinemarr searches recognized older PAmpMod/Cinemarr loader filenames, imports supported values once, writes the canonical file, and leaves the source untouched. Malformed or out-of-range values reject initialization without echoing secrets. `CINEMARR_PLEX_TOKEN` always overrides a file token.

Minecraft itself controls whether a world can be upgraded between game versions. Cinemarr migration support is not a promise that vanilla downgrades are safe.
