# Prerelease implementation status

This checkout is a Cinemarr 1.0.0 prerelease under hardening. It is not a
validated release candidate and has not been tagged or published. Evidence in
this document predating protocol 10 is regression history only; every required
gate must be repeated against the exact final commit and artifact bytes.

| Area | Current state | Required before release |
| --- | --- | --- |
| Product boundary | Video-only protocol 10; inherited music/station UI, transport, persistence, tests, JLayer, and Jump3r remain removed | Rebuild and inspect all 16 exact final JARs |
| Platform matrix | Code commit `1cbd341` passed the complete local 16-artifact/21-runtime matrix, all 10 GameTests, and fully green hosted CI run `33392358275` with bundle parity | Repeat CI for the final documentation SHA and preserve exact bundle parity |
| Plex security | Server-only credentials, allowlists, origin confinement, redaction, and bounded recovery remain implemented | Repeat credentialed recovery and exact-secret scans |
| Screens | Persistent geometry, ownership, overlap, lifecycle limits, and 10 GameTests remain implemented | Repeat all GameTests and real-client ownership checks |
| Quick TVs | Bounded construction, transactional rollback, and schema-3 recovery remain implemented | Repeat restart and unloaded-footprint recovery on exact candidate bytes |
| Controller UI | Pagination, session draft retention, `canControl`, queue, stream, and presentation controls remain implemented | Repeat legacy draft-state and representative real-client layout checks |
| Media relay | Bounded work/cache/egress, fairness tests, EOS handling, sustained latency, transient failure, exhaustion, and recovery are locally green | Repeat credentialed recovery on exact deployed bytes |
| Client decode | Software remains the release baseline; auto/VAAPI remain experimental | Repeat software A/V and packaged native smoke against exact candidate bytes |
| Release evidence | Current fake-Plex, GameTest, artifact, reproducibility, hygiene, hosted-CI, and bundle-parity gates are green for `1cbd341`; the attempted exact external run stopped before opening clients when DiscPanel failed a rollback upload/server update, and its credential holder then exited | Complete exact real-Plex, recovery, lifecycle, configured-secret, and native recertification, then obtain green CI for the final documentation SHA |

The authoritative remaining work is [the 1.0 hardening plan](1.0_RELEASE_HARDENING_PLAN.md)
and the unchecked [release acceptance](RELEASE_ACCEPTANCE.md) checklist. No tag
or publication is authorized by completing those engineering gates.

Jammarr target-expansion lessons and loader constraints are recorded in
[JAMMARR_TARGET_FEASIBILITY.md](JAMMARR_TARGET_FEASIBILITY.md); they do not
expand the Cinemarr 1.0 release matrix.
