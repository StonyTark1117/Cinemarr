# Release acceptance

Status: **release-ready; not yet tagged or published**. This checklist applies to the exact Cinemarr 1.0.0 protocol-9 artifacts proposed for publication. Historical 1.0.1 or fake-Plex results do not satisfy it.

## Code and artifact gates

- [x] The worktree is intentionally scoped and the release commit is identified.
- [x] Unit tests and all 10 required GameTests pass under the required Java toolchain.
- [x] All 16 canonical JARs build from that commit.
- [x] Artifact inspection verifies identity/version, protocol 9, loader metadata, Java bytecode level, native classifiers, licenses, recipes/assets, and no credentials.
- [x] No JAR contains the removed music/station runtime, JLayer, or Jump3r.
- [x] SHA-256 digests are recorded for every artifact.

The 16 canonical JARs build and inspect from the scoped release tree. Two consecutive full-matrix builds produced the same complete `build/releases/SHA256SUMS` manifest, and inspection now rejects wall-clock ZIP timestamps that would make a nominally identical rebuild drift.

## Runtime matrix

- [x] All 21 maintained runtime profiles start a dedicated server and two clients.
- [x] Each profile constructs a real 144p Quick TV through normal block behavior and persists 16x9 geometry with a 256x144 rendition.
- [x] Both clients render identifiable video, produce audible synchronized audio, survive reconnect, and leave no process/port/audio-module residue.
- [x] Forge 1.7.10 is run fresh twice because its Java 8/LWJGL 2 path is independent.
- [x] Quilt profiles use the Fabric artifact with the intended Quilt runtime.

## Failure and lifecycle gates

- [x] Plex-disabled, connecting, ready, degraded, automatic retry, and operator retry states behave consistently at the legacy Forge, Fabric/Quilt, modern NeoForge, and 26.2 Fabric boundaries.
- [x] Configured credentials and the private Plex endpoint are absent from source, logs, diagnostics, saves, runtime evidence, and release artifacts.
- [x] Segment retry exhaustion reports a bounded redacted error without wedging the session.
- [x] Quick TV obstruction, chunk unload, controller removal, server stop, and restart-mid-build all roll back generated pixels without removing player-built screens.
- [x] Unloaded Quick TV recovery footprints remain persisted until their chunks can be inspected.
- [x] Non-owners can view but cannot mutate a session unless permission policy grants control.
- [x] Removing the final television checkpoints and closes the Plex transcode; removing one of several attached TVs does not.

## Native host gates

- [x] The packaged `linux-arm64` classifier decodes 144p, 480p, and 1080p fixtures in software under an aarch64 kernel and aarch64 Java runtime.
- [x] The packaged `windows-x86_64` classifier decodes the same fixtures in software under native Windows x86-64 and its FFmpeg DLL reports PE machine `0x8664`.
- [x] Both native runs leave their tagged hypervisor resources clean.

## Decisive real-Plex gate

- [x] The in-game controller/UI browses and selects an identifiable item from an allowed real Plex library.
- [x] Two independent clients show matching identifiable program video.
- [x] Both clients produce synchronized audible program audio within the documented threshold.
- [x] Pause, seek, stream selection, stop, disconnect/reconnect, and final-TV teardown behave correctly.
- [x] Plex reports no leftover session/transcode and the host has no leftover game process, port, temporary credential file, or credential-bearing log.

The opt-in command is:

```bash
CINEMARR_VIDEO_CLIENT_GATE=true \
CINEMARR_LIVE_PLEX_GATE=true \
CINEMARR_PLEX_URL='http://plex.example.invalid:32400' \
CINEMARR_PLEX_TOKEN='...' \
CINEMARR_LIVE_VIDEO_SECTION_ID='1' \
./scripts/run-dedicated-server-gate.sh 1.21.1-neoforge
```

Fake-Plex gates are regression evidence only. Publication must use the final artifact hashes tied to this completed checklist.

## Current regression evidence

On 2026-08-29, the deterministic fake-Plex gate passed all 21 maintained profiles with two clients, identifiable matching video, synchronized audible output, command-permission and protocol-mismatch probes, software decoding, and clean process/port teardown. The canonical build also passed all 10 required GameTests, rebuilt and inspected 16 artifacts, and regenerated matching SHA-256 manifests.

On 2026-08-30, the exact indexed and deployed artifacts for Forge 1.7.10, Quilt 1.20.1, NeoForge 1.21.1, and Fabric 26.2 passed controller-selected playback against a credentialed real Plex server. Each run produced matching identifiable program video and correlated audible output on two clients, reported zero video drops and audio underruns, drained its television and Plex stream state to baseline, restored server settings exactly, and left the managed servers stopped with autostart disabled. Representative Forge 1.7.10 and NeoForge 1.21.1 runs additionally passed pause/resume, seek, alternate audio/subtitle selection, follower disconnect/reconnect, final-TV teardown, and a 640x360 non-owner UI with zero clipped widgets and rejected control mutation. Forge 1.7.10 then completed three fresh full-control/reconnect runs on exact digest `37232612d83ad36729cdfaab3eb36c5736c18b0b86d021136e000c2a05b5495b`; the paired program-audio correlations were 0.759449, 0.780179, and 0.752659, all at 0 ms measured lag. Evidence is retained under `build/discopanel-real-plex/`.

The exact final Forge 1.7.10 and NeoForge 1.21.1 artifacts also passed real server-stop/restart during Quick TV construction, persisted an unloaded recovery footprint, rolled back loaded pixels first, completed rollback after the footprint chunks were loaded, and left the first generated pixel as air. Evidence is retained under `build/discopanel-lifecycle/`.

Credentialed recovery passed disabled, degraded, operator-retry, automatic-retry, and restored-ready behavior on the exact four representative artifacts under `build/discopanel-plex-recovery/`. Legacy Forge 1.7.10 and modern NeoForge 1.21.1 passed transient segment recovery, bounded exhaustion, redacted failure, and same-session recovery under `build/adverse-network/`. Native Linux ARM64 evidence is retained under `build/native-smoke/linux-arm64/20260830T125759Z/`; native Windows x86-64 evidence is retained under `build/native-smoke/windows-x86_64/20260830T174136Z/`. A scan using the actual configured credentials and endpoint found zero matches across tracked and untracked source plus retained runtime/release evidence. All 21 managed servers were stopped with autostart disabled, and every native-test manifest was clean.

This checklist is complete. Tagging and publication remain separate, explicitly authorized release operations.
