# Retained native test guests

Cinemarr's Windows x86-64 and Linux ARM64 decoder gates use dedicated, headless QEMU guests on the Proxmox test host. Their installed disks are retained so repeated release checks do not reinstall an operating system. A test run creates a uniquely tagged transient systemd unit, and every success or failure path stops that unit. The guests must be powered off between runs.

These are host-managed QEMU guests, not registered Proxmox `qm` VM entries.
Their absence from the Proxmox VM list does not mean their installed disks were
deleted. Use the retained-state audit below to verify readiness and power state.
This gate tests the native decoder payload; it does not certify a complete
Minecraft client on Windows or ARM.

These scripts do not attach to the user's desktop or open a graphical window. Windows uses QEMU `-display none`; ARM64 uses `-nographic`. Minecraft client gates use `scripts/run-private-xvfb.sh`: each client gets a separately bound X server in the 90–190 display range, with TCP disabled and owned-process cleanup. The launcher does not inherit or connect to the user's desktop. Allocation and signal-cleanup regressions run through `scripts/test-private-xvfb.py`.

## Persistent state

The Proxmox host retains only the prepared guest state:

| Guest | Host state directory | Reused credential |
| --- | --- | --- |
| Windows 11 x86-64 | `/var/lib/cinemarr-hwtest/windows-x86_64` | In-guest startup task; no remote login credential |
| Debian Linux ARM64 | `/var/lib/cinemarr-hwtest/linux-arm64` | Dedicated Ed25519 key under the invoking user's local data directory |

Each directory is mode `0700` and contains `state.json` with an exact platform identity. The runner refuses an absent, malformed, non-ready, or mismatched marker instead of guessing that an arbitrary directory is safe. The ARM private key defaults to `~/.local/share/cinemarr-hwtest/linux-arm64/id_ed25519`, remains outside the repository, and is forced to mode `0600`.

The Windows guest installs an at-startup `CinemarrNativeSmoke` scheduled task during its first acceptance run. On later boots that task waits for the fresh `CINEMARR` payload ISO and `CINEVIDENCE` disk, runs the current benchmark bundle, writes evidence, and powers the guest off. The ARM guest retains its cloud image overlay and Java/native ABI packages; each run transfers the current bundle over its dedicated SSH identity and powers the guest off after evidence retrieval.

## First provisioning and later reuse

Prepare the current decoder bundle first, using JDK 21 for the root Gradle
wrapper. Do not overlap this Gradle invocation with release builds or physical
audio acceptance runs:

```bash
JAVA_HOME='/path/to/jdk-21' \
./gradlew --no-daemon --no-configuration-cache prepareDecoderBenchmarkBundle
```

Pass the Proxmox credential file as a file path. Do not copy its contents into the repository or persist them in an environment profile.

```bash
CINEMARR_HWTEST_HOST='proxmox.example.invalid' \
CINEMARR_HWTEST_PASSWORD_FILE='/path/to/proxmox-password-file' \
CINEMARR_WINDOWS_ISO='/path/to/Windows11.iso' \
./scripts/run-windows-x64-native-smoke.sh

CINEMARR_HWTEST_HOST='proxmox.example.invalid' \
CINEMARR_HWTEST_PASSWORD_FILE='/path/to/proxmox-password-file' \
./scripts/run-linux-arm64-native-smoke.sh
```

On first use, the Windows run installs the operating system and the ARM run downloads and prepares Debian. Later invocations validate `state.json`, reuse the prepared disk, and attach only current payload/evidence media. Windows still requires `CINEMARR_WINDOWS_ISO` so its checksum remains part of the native evidence contract, but a ready retained guest does not upload or boot the installer.

Every result is written below `build/native-smoke/<platform>/<UTC timestamp>/`. It includes benchmark JSON/CSV, system identity, input/output checksums, the transient-resource manifest, and `retained-vm-audit.json`. `run-id.txt` binds evidence to the current invocation so stale files cannot satisfy the gate.

## Required stopped-state audit

After both guests have been provisioned, run the read-only audit:

```bash
CINEMARR_HWTEST_HOST='proxmox.example.invalid' \
CINEMARR_HWTEST_PASSWORD_FILE='/path/to/proxmox-password-file' \
./scripts/audit-retained-native-smoke-vms.sh
```

Success requires both ready identity markers, both prepared disks, and no QEMU process using either disk. A native gate is incomplete if its benchmark passes but this stopped-state assertion fails.

The September 7 candidate reuse completed without reinstalling either guest:
Windows run `20260907T182033Z` and ARM run `20260907T182433Z` passed all three
decoder resolutions. `build/native-smoke/retained-vm-audit-20260907-packaged.json`
records both installed disks ready and off afterward. Exact native-payload
parity with all sixteen candidate JARs is recorded separately in
`build/native-smoke/20260907-packaged-native-parity.json`. This receipt is
historical evidence for those candidate bytes; run a fresh stopped-state audit
before reuse. ARM is QEMU-emulated functional/ABI coverage, not a physical-ARM
performance measurement or full Minecraft-client acceptance.

A fresh read-only preflight on September 7, after the decoder-budget changes,
again verified both exact ready markers and installed disks with neither
guest running (`build/native-smoke/retained-vm-audit-20260907-r3-preflight.json`).
The supplied Proxmox password file authenticated successfully. This preflight
did not boot, reinstall or modify either guest and is not new decoder
acceptance; both must still test the final candidate and pass a post-run
stopped-state audit.

The r3 candidate reuse on September 8 UTC also passed without provisioning:
Windows `20260908T035508Z` and ARM `20260908T035922Z` each passed all three
decoder resolutions. `build/native-smoke/retained-vm-audit-20260907-release-audit-r3.json`
records both retained guests installed, ready and off after the runs.
`build/native-smoke/20260907-release-audit-r3-native-parity.json` matches the
tested Windows/ARM native entries and shared decoder/core class payloads
across all sixteen r3 candidate JARs. This remains scoped to those exact
payloads, not an untested later artifact or a complete Minecraft client.
The Windows installer was checksum-checked locally but was not uploaded or
booted; ARM startup SSH retries completed within the original run, which was
not restarted to obtain success.

## Failure and reprovisioning

The cleanup trap always stops the uniquely tagged transient unit. A failed first provisioning deliberately leaves `state.json` in `provisioning` state for diagnosis, and a failed run retains its Windows per-run handoff directory. The runner will not delete or overwrite either automatically.

Before reprovisioning, inspect the exact state directory, confirm no QEMU process references its disk, archive any evidence needed for diagnosis, and remove only that platform's validated directory and its ARM key when intentionally rotating the ARM identity. Never use a wildcard or remove `/var/lib/cinemarr-hwtest` as a whole. A Windows installer refresh or ARM base-image refresh is an explicit reprovisioning event, not part of an ordinary rerun.
