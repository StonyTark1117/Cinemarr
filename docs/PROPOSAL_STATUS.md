# Prerelease implementation status

This checkout is a Cinemarr 1.0.0 prerelease under hardening, with runtime-certified artifact evidence. Release readiness is assessed by the
final containing commit’s local and hosted acceptance gates. No tag or publication is authorized by this plan.

The authoritative current checkpoint and remaining work are in the
[1.0 release hardening plan](1.0_RELEASE_HARDENING_PLAN.md). Its original ten
phases retain the 16-artifact / 21-runtime scope. Detailed failed, interrupted,
partial and source-bound accepted runs are distinguished in
[release acceptance](RELEASE_ACCEPTANCE.md). The [candidate evidence manifest](RELEASE_CANDIDATE_EVIDENCE_20260914.json)
records the current tested product bytes and scoped receipt hashes. Final
commit-level acceptance requires matching local/hosted artifacts and clean Git state.

Current implementation includes shared media/health policies, bounded
asynchronous work and egress, required-client negotiation, legacy persistence
and reload fixes, connection-owned client cleanup, and responsive controller
behavior. Live review has continued to uncover defects, including browse
starvation, retained rate-limit subjects and a multi-client egress teardown
exception. These fixes have regression coverage; exact final-byte certification
and fully green final-SHA GitHub CI with local/hosted hash parity remain open.

The complete local `releaseMatrixGate` now requires all maintained artifact and
runtime targets plus terminal, pressure, post-fault, Quilt Mod Menu and minimum
Fabric Loader supplements. Its automated result must still be paired with
direct visual review, physical audio, reproducibility, exact packaged real-Plex
and native guest acceptance, security review and verified teardown. The
[acceptance runbook](PACKAGED_CLIENT_ACCEPTANCE.md) documents these boundaries.

Earlier detailed implementation checkpoints and their superseded table are
preserved verbatim in [historical evidence](HARDENING_EVIDENCE_HISTORY.md).
They describe older candidates, not the current release state.

The [current Jammarr comparison](JAMMARR_COMPARISON_20260914.md) was completed
at the user's explicit request while release acceptance was paused. It compares
GitHub's current source and published release, identifies useful fixes and future
ports, and leaves this release matrix and its unfinished gates unchanged.
