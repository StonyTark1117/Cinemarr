# Proposal implementation status

This checkout is the validated 1.0.0 release candidate. It has not been tagged or published.

| Area | Current state | Required before release |
| --- | --- | --- |
| Product boundary | Video-only protocol 9; inherited music/station UI, transport, persistence, tests, JLayer, and Jump3r removed; the 16 rebuilt JARs pass the forbidden-entry inspection | Complete |
| Platform matrix | All 16 canonical artifacts rebuild and inspect; the deterministic fake-Plex two-client gate passed all 21 runtime profiles; exact deployed artifacts passed real-Plex two-client A/V on Forge 1.7.10, Quilt 1.20.1, NeoForge 1.21.1, and Fabric 26.2; Forge 1.7.10 passed three fresh complete control/reconnect runs at 0 ms measured lag; Linux ARM64 and Windows x86-64 native smoke pass | Complete |
| Plex security | Server-only token, allowlists, rating/permission filtering, confined HLS, redaction, asynchronous state/retry parity across representative adapter boundaries; credentialed failure/recovery and actual-secret scans pass | Complete |
| Screens | Persistent geometry, ownership, overlap and lifecycle limits; all 10 required GameTests pass; representative non-owner viewing and mutation rejection passed against real Plex | Complete |
| Quick TVs | Bounded default, literal ceiling, <=256 placements/tick, transactional rollback and schema-3 recovery ledger; exact Forge 1.7.10 and NeoForge 1.21.1 artifacts passed restart/unloaded-footprint recovery | Complete |
| Controller UI | Pagination, session draft retention, `canControl` enforcement, queue/stream/presentation controls, visible errors; the 640x360 non-owner UI rendered with zero clipped widgets | Complete |
| Media relay | Rendition caps, bounded/hash-checked segments, retries and diagnostics; legacy and modern adverse-network/exhaustion/recovery gates pass | Complete |
| Client decode | Software default; only software/auto/vaapi public modes; software two-client A/V passed all 21 deterministic profiles; packaged Linux ARM64 and Windows x86-64 native smoke pass | Complete |
| Release evidence | Unit tests, 10 GameTests, 16-artifact inspection/hashes, the 21-profile fake-Plex matrix, representative real-Plex controls/reconnect/non-owner UI, three fresh Forge 1.7.10 reconnect soaks, old/new-loader restart recovery, credentialed recovery, adverse-network behavior, Linux ARM64 and Windows x86-64 native smoke, secret/residue audit, and two identical full-matrix SHA-256 manifests pass in this checkout | Complete |

Every item in [release acceptance](RELEASE_ACCEPTANCE.md) is checked against the final artifact digests. No tag or publication has been performed.
