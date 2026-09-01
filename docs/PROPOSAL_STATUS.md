# Prerelease implementation status

This checkout is a Cinemarr 1.0.0 prerelease under hardening. It is not a
validated release candidate and has not been tagged or published. Evidence in
this document predating protocol 10 is regression history only; every required
gate must be repeated against the exact final commit and artifact bytes.

| Area | Current state | Required before release |
| --- | --- | --- |
| Product boundary | Video-only protocol 10; inherited music/station UI, transport, persistence, tests, JLayer, and Jump3r remain removed; the rebuilt 16-JAR index passed deep inspection | Preserve the exact bundle through hosted CI |
| Platform matrix | The current tree passed all 16 artifact targets, all 10 required GameTests, and a fresh complete 21-runtime matrix in 53 minutes 35 seconds | Repeat the complete CI matrix for the exact pushed SHA |
| Plex security | Server-only credentials, allowlists, origin confinement, redaction, and bounded recovery passed the exact four-profile recovery matrix; a live-value source/evidence/JAR scan found zero matches | Preserve redaction and secret-scan results through final CI |
| Screens | Persistent geometry, ownership, overlap, lifecycle limits, and 10 GameTests passed; representative owner/non-owner real-client layouts passed on private Xvfb displays | Preserve the exact client behavior through final CI |
| Quick TVs | Exact Forge 1.7.10 and NeoForge 1.21.1 artifacts passed restart-mid-build and unloaded-footprint recovery | Preserve lifecycle behavior through final CI |
| Controller UI | Pagination, session draft retention, `canControl`, queue, stream, and presentation controls passed the exact representative real-Plex gates | Preserve exact behavior through final CI |
| Media relay | Bounded work/cache/egress, fairness, EOS handling, sustained latency, transient failure, exhaustion, and recovery are green on the current representative bytes | Preserve exact behavior through final CI |
| Client decode | Software remains the release baseline; the exact Windows x86-64 and Linux ARM64 packages each passed provisioning and a second retained-guest reuse boot, with both guests stopped afterward | Preserve packaged bytes through final hosted-bundle parity |
| Release evidence | Local build/inspection, exact DiscPanel deployment, real-Plex, recovery, lifecycle, live-secret scan, and retained native-guest gates are green on the working tree | Commit and push the scoped tree, obtain fully green exact-SHA CI, download the hosted bundle, and prove byte parity |

The authoritative remaining work is [the 1.0 hardening plan](1.0_RELEASE_HARDENING_PLAN.md)
and the unchecked [release acceptance](RELEASE_ACCEPTANCE.md) checklist. No tag
or publication is authorized by completing those engineering gates.

Jammarr target-expansion lessons and loader constraints are recorded in
[JAMMARR_TARGET_FEASIBILITY.md](JAMMARR_TARGET_FEASIBILITY.md); they do not
expand the Cinemarr 1.0 release matrix.
