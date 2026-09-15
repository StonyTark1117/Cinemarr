# Release audit pause — September 14, 2026

Work is paused at the user's request. This is a progress checkpoint, not release
certification. No tag or release has been published, and `main` has not been
advanced. Resume from `codex/release-audit-remediation-20260913`.

## Completed

- Audit remedies and follow-up fixes are committed through `6e568fb`, including
  legacy text selection, protocol rejection, NeoForge early-window configuration,
  settled non-owner capture selection, and both modern saved-player schemas.
- Two forced builds produce the same eighteen-file bundle. The original-push
  [CI run 34907274193](https://github.com/StonyTark1117/Cinemarr/actions/runs/34907274193)
  on `4187962` passes all eighteen jobs; its bundle matches both forced builds.
- All twenty-one hosted main profiles and five Quilt/Mod Menu cases have direct
  playback/controller review, physical PCM verification, feature checks and
  fresh-server persistence checks. All twenty-six controlled feature records pass.
- Four deployed profiles pass manual and automatic Plex outage recovery.
- Current-artifact real-Plex main runs pass on Forge 1.7.10, Quilt 1.20.1,
  NeoForge 1.21.1 and Fabric 26.2. Every run restores server configuration.
  Legacy, Quilt and NeoForge each have all 96 original screenshots directly
  reviewed. Fabric's records pass; its 96 screenshots still need direct review.
- NeoForge's failed first live attempt remains preserved. The corrected attempt
  passes, with both saved players restored byte for byte. Fabric likewise passes
  with both players restored. Their audio correlations are 0.994324 and 0.99355,
  respectively, both at 0 ms lag.
- Current controlled display/rendering direct reviews cover legacy Forge,
  NeoForge 1.21.1, NeoForge 26.1.2 and Fabric 26.2. Windows x64 and emulated Linux
  ARM64 native component evidence remains applicable to the unchanged native
  payload; it is not full Minecraft acceptance on those operating systems.
- Relevant regression suites and scoped configured-secret scans pass. The latest
  Fabric/NeoForge scan covers 404 files with fourteen configured values, four
  baseline Plex URLs, no findings and no input changes.

## Remaining before release certification

1. Directly review Fabric's 96 live screenshots, the Quilt 1.20.1 and Forge
   1.20.2 controlled display boundaries, and remaining adverse/minimum-loader
   evidence. Preserve the distinction between historical and current artifacts.
2. Run current-artifact lifecycle cases for Forge 1.7.10 and NeoForge 1.21.1.
3. Reconcile the candidate manifest, acceptance record, plans and release metadata
   to the final evidence. Historical green results do not certify later commits.
4. Run the complete 37-case local matrix and original-push eighteen-job hosted
   CI on the final containing commit, including the three latest harness fixes.
   Verify artifact parity and final scans; then synchronize `main` normally.
5. Complete final cleanup verification and the deferred fresh Jammarr feasibility
   assessment after release acceptance. Do not publish a tag or release.

## Cleanup and resumption

At the pause, all four test servers independently pass all six restoration
checks: stopped, autostart disabled, original overrides and configuration bytes
restored, Cinemarr disabled, and other mods preserved. Both native VMs are stopped.
No audit acceptance service is running.

Pause cleanup receipts and redacted evidence are retained locally under
`build/audit-remediation-evidence/loader-window-certification/`. Large runtime
artifacts, failed attempts, rollback binaries and the Jammarr checkout are kept.
The owned remote recovery workspace and temporary sensitive copies are removed
after restoration and scans. The user's original credential files are preserved.
Re-discover endpoints and create fresh private baselines before another live run;
old private wrappers may refer to files intentionally removed during cleanup.

The detailed local continuation record is
`build/audit-remediation-evidence/continuation-20260914.md`. Its older active-job
instructions are superseded by this pause. The frozen artifact/runtime checkout
remains `Cinemarr-release-loader-window-20260914` at `4187962`; later harness fixes
live in the primary branch and require the final containing-commit gates above.
