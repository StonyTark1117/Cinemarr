# Prerelease implementation status

This checkout is a Cinemarr 1.0.0 prerelease under hardening. It is not a
validated release candidate and has not been tagged or published. Evidence in
this document predating protocol 10 is regression history only; every required
gate must be repeated against the exact final commit and artifact bytes.

| Area | Current state | Required before release |
| --- | --- | --- |
| Product boundary | Video-only protocol 10; inherited music/station UI, transport, persistence, tests, JLayer, and Jump3r remain removed | Rebuild and inspect all 16 exact final JARs |
| Platform matrix | Protocol 10 passed the complete manifest-derived 16-artifact/21-runtime local matrix | Obtain green CI and hosted-bundle parity for the exact pushed SHA |
| Plex security | Server-only credentials, allowlists, origin confinement, redaction, and bounded recovery remain implemented | Repeat credentialed recovery and exact-secret scans |
| Screens | Persistent geometry, ownership, overlap, lifecycle limits, and 10 GameTests remain implemented | Repeat all GameTests and real-client ownership checks |
| Quick TVs | Bounded construction, transactional rollback, and schema-3 recovery remain implemented | Repeat restart and unloaded-footprint recovery on exact candidate bytes |
| Controller UI | Pagination, session draft retention, `canControl`, queue, stream, and presentation controls remain implemented | Repeat legacy draft-state and representative real-client layout checks |
| Media relay | Bounded work/cache/egress, fairness tests, EOS handling, sustained latency, transient failure, exhaustion, and recovery are locally green | Repeat credentialed recovery on exact deployed bytes |
| Client decode | Software remains the release baseline; auto/VAAPI remain experimental | Repeat software A/V and packaged native smoke against exact candidate bytes |
| Release evidence | Current fake-Plex, GameTest, artifact, reproducibility, and hygiene gates are green; real-Plex evidence remains historical | Complete exact real-Plex recertification, then obtain green CI for the exact pushed SHA |

The authoritative remaining work is [the 1.0 hardening plan](1.0_RELEASE_HARDENING_PLAN.md)
and the unchecked [release acceptance](RELEASE_ACCEPTANCE.md) checklist. No tag
or publication is authorized by completing those engineering gates.
