# Release acceptance

Status: **1.0 prerelease under hardening; not release-candidate ready**. This checklist must be completed again for the exact final commit and artifact bytes proposed for publication. Historical candidate or fake-Plex results do not satisfy it.

## Code and artifact gates

- [ ] The worktree is intentionally scoped and the release commit is identified.
- [x] Unit tests and all 10 required GameTests pass under the required Java toolchain.
- [x] All 16 canonical JARs build from the current hardening tree.
- [x] Artifact inspection verifies identity/version, protocol, loader metadata, Java bytecode level, native classifiers, licenses, recipes/assets, and no credentials.
- [x] No JAR contains the removed music/station runtime, JLayer, or Jump3r.
- [x] SHA-256 digests are recorded for every artifact.

The checks above were repeated for code commit `1cbd341b88023bffce340963f1890428f5be9712` locally and in GitHub Actions run `33392358275`. Later commits `63e0150`, `9adb3c4`, `2de12a7`, and `501c9c6` change only release operations and CI. Commit `21431bf` changes packaged client audio startup behavior; its nine affected artifact targets and focused 1.21.1 Fabric runtime gate pass locally, but all 16 final artifacts still require a clean hosted run and bundle-parity check.

## Runtime matrix

- [x] All 21 maintained runtime profiles start a dedicated server and two clients.
- [x] Each profile constructs a real 144p Quick TV through normal block behavior and persists 16x9 geometry with a 256x144 rendition.
- [x] Both clients render identifiable video, produce audible synchronized audio, survive reconnect, and leave no process/port/audio-module residue.
- [x] Forge 1.7.10 is run fresh twice because its Java 8/LWJGL 2 path is independent.
- [x] Quilt profiles use the Fabric artifact with the intended Quilt runtime.

## Failure and lifecycle gates

- [ ] Plex-disabled, connecting, ready, degraded, automatic retry, and operator retry states behave consistently at the legacy Forge, Fabric/Quilt, modern NeoForge, and 26.2 Fabric boundaries.
- [ ] Configured credentials and the private Plex endpoint are absent from source, logs, diagnostics, saves, runtime evidence, and release artifacts.
- [x] Segment latency, transient failure, and retry exhaustion report bounded redacted behavior and recover without wedging the session.
- [ ] Quick TV obstruction, chunk unload, controller removal, server stop, and restart-mid-build all roll back generated pixels without removing player-built screens.
- [ ] Unloaded Quick TV recovery footprints remain persisted until their chunks can be inspected.
- [ ] Non-owners can view but cannot mutate a session unless permission policy grants control.
- [ ] Removing the final television checkpoints and closes the Plex transcode; removing one of several attached TVs does not.

## Native host gates

- [ ] The packaged `linux-arm64` classifier decodes 144p, 480p, and 1080p fixtures in software under an aarch64 kernel and aarch64 Java runtime.
- [ ] The packaged `windows-x86_64` classifier decodes the same fixtures in software under native Windows x86-64 and its FFmpeg DLL reports PE machine `0x8664`.
- [ ] Both native runs leave their tagged hypervisor resources clean.

## Decisive real-Plex gate

- [ ] The in-game controller/UI browses and selects an identifiable item from an allowed real Plex library.
- [ ] Two independent clients show matching identifiable program video.
- [ ] Both clients produce synchronized audible program audio within the documented threshold.
- [ ] Pause, seek, stream selection, stop, disconnect/reconnect, and final-TV teardown behave correctly.
- [ ] Plex reports no leftover session/transcode and the host has no leftover game process, port, temporary credential file, or credential-bearing log.

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

GitHub Actions run `33398719853` passed 15 artifact/runtime jobs, including
deterministic rebuilds and uploads, but correctly failed 1.21.1 Fabric because
the two clients' highly correlated program audio was 330 ms apart. The
aggregate gate was skipped rather than masking the failure with a rerun.
Commit `21431bfb44c051b6fb9d02a080afe1355f05eb0b` fixes the OpenAL
source-start cursor that could move backward when processed streaming buffers
were unqueued. Its private-Xvfb 1.21.1 Fabric gate passed with matching
identifiable frames, zero underruns, 0.996978 correlation, and 80 ms lag; unit
tests and all nine affected 1.21.1/26.1.2/26.2 artifact verification targets
also passed. This does not check the remaining exact-byte external or native
boxes and still requires clean hosted CI.

On 2026-08-31, code commit `1cbd341b88023bffce340963f1890428f5be9712` passed all 16 local artifact verification targets, all 10 required GameTests, and the complete 21-runtime/two-client matrix using private Xvfb displays. Final Forge 1.7.10 and NeoForge 1.21.1 adverse-network runs passed slow delivery, transient recovery, bounded exhaustion, redacted failure, same-session recovery, and cleanup. GitHub Actions run `33392358275` passed manifest validation, all 16 artifact/runtime jobs, byte-identical rebuilds, and the non-skipped aggregate `1.0 release gate`. The hosted 16-JAR bundle passed deep inspection and `SHA256SUMS`, and `build/releases` was indexed from and compared byte-for-byte with that bundle. Exact representative hashes are `d36fd1799feff72df36f59df39c086765afb27c8e3e23f1554cb6215538e4565` (Forge 1.7.10), `05cb545a741223fdcbe2b8affc3b5c42e671cd6429a55e3d2500e6610bb48350` (Fabric/Quilt 1.20.1), `ab22be1f8cf1498556414859803ebee49acf6776d17a7adfc5fa5b5018944f92` (NeoForge 1.21.1), and `87587a77f2c8361b5ee049c2c33699934334a647617b084333834836c6e3a7f3` (Fabric 26.2).

The exact hosted bundle was staged for the four stopped, autostart-disabled DiscPanel representatives. The first three replacements retained disabled, hash-verified rollback copies. The 26.2 rollback upload returned HTTP 500, and the subsequent 1.7.10 acceptance preparation could not update server state after five retries, so no client GUI was opened and no exact-byte external box is checked. Commit `63e0150` makes rollback and canonical upload/import failures explicit fatal boundaries so that this partial state cannot be reported as a successful deployment again. Commit `2de12a7` additionally requires exactly one active Cinemarr artifact and a disabled rollback whose downloaded bytes match its recorded hash prefix even when the active candidate is already exact. Commit `501c9c6` preserves the original nonzero status through the rollback trap, disables a failed replacement, re-enables the verified backup, and verifies that backup is the only active Cinemarr artifact. The DiscPanel credential-holder process exited with the failed workflow; current remote state must be re-audited read-only before any retry. The candidate and previously native-certified artifacts contain the same decoder classes and all 618 identical native entries, but exact-candidate real-Plex, recovery, lifecycle, configured-secret, and packaged-native boxes remain unchecked.

The same day, a real-Plex Forge 1.7.10 diagnostic exposed and then verified the fix for a multi-window video-transfer deadlock. Both clients rendered matching program video with audible synchronized audio and passed controls, reconnect, and cleanup. The server used the preceding candidate during that diagnostic, so this is regression evidence only and does not check the decisive exact-byte boxes.

On 2026-08-30, the protocol-10 hardening working tree passed the complete 21-runtime manifest matrix with private per-client Xvfb displays, identifiable video, correlated audible output, controls, protocol/command probes, and residue-free teardown. The finalized Forge 1.7.10 and NeoForge 1.21.1 adverse-network profiles then passed sustained 100 ms-per-segment latency, transient 503 recovery, bounded exhaustion, redacted diagnostics, same-session recovery, and clean teardown. All 16 artifacts passed deep inspection and checksum verification; forced rebuild evidence was byte-identical, including the four Forge-family archives whose local extended timestamps were normalized. This is precommit regression evidence only and does not check the remaining real-Plex or pushed-SHA boxes.

On 2026-08-29, the deterministic fake-Plex gate passed all 21 maintained profiles with two clients, identifiable matching video, synchronized audible output, command-permission and protocol-mismatch probes, software decoding, and clean process/port teardown. The canonical build also passed all 10 required GameTests, rebuilt and inspected 16 artifacts, and regenerated matching SHA-256 manifests.

On 2026-08-30, the exact indexed and deployed artifacts for Forge 1.7.10, Quilt 1.20.1, NeoForge 1.21.1, and Fabric 26.2 passed controller-selected playback against a credentialed real Plex server. Each run produced matching identifiable program video and correlated audible output on two clients, reported zero video drops and audio underruns, drained its television and Plex stream state to baseline, restored server settings exactly, and left the managed servers stopped with autostart disabled. Representative Forge 1.7.10 and NeoForge 1.21.1 runs additionally passed pause/resume, seek, alternate audio/subtitle selection, follower disconnect/reconnect, final-TV teardown, and a 640x360 non-owner UI with zero clipped widgets and rejected control mutation. Forge 1.7.10 then completed three fresh full-control/reconnect runs on exact digest `37232612d83ad36729cdfaab3eb36c5736c18b0b86d021136e000c2a05b5495b`; the paired program-audio correlations were 0.759449, 0.780179, and 0.752659, all at 0 ms measured lag. Evidence is retained under `build/discopanel-real-plex/`.

The exact final Forge 1.7.10 and NeoForge 1.21.1 artifacts also passed real server-stop/restart during Quick TV construction, persisted an unloaded recovery footprint, rolled back loaded pixels first, completed rollback after the footprint chunks were loaded, and left the first generated pixel as air. Evidence is retained under `build/discopanel-lifecycle/`.

Credentialed recovery passed disabled, degraded, operator-retry, automatic-retry, and restored-ready behavior on the exact four representative artifacts under `build/discopanel-plex-recovery/`. Legacy Forge 1.7.10 and modern NeoForge 1.21.1 passed transient segment recovery, bounded exhaustion, redacted failure, and same-session recovery under `build/adverse-network/`. Native Linux ARM64 evidence is retained under `build/native-smoke/linux-arm64/20260830T125759Z/`; native Windows x86-64 evidence is retained under `build/native-smoke/windows-x86_64/20260830T174136Z/`. A scan using the actual configured credentials and endpoint found zero matches across tracked and untracked source plus retained runtime/release evidence. All 21 managed servers were stopped with autostart disabled, and every native-test manifest was clean.

These entries are historical evidence only. They must not be converted back into checked current-release claims until exact final candidate bytes have passed the corresponding gates. Tagging and publication remain separate, explicitly authorized release operations.
