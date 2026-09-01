# Retained native test guests

Cinemarr's Windows x86-64 and Linux ARM64 decoder gates use dedicated, headless QEMU guests on the Proxmox test host. Their installed disks are retained so repeated release checks do not reinstall an operating system. A test run creates a uniquely tagged transient systemd unit, and every success or failure path stops that unit. The guests must be powered off between runs.

These scripts do not attach to the user's desktop or open a graphical window. Windows uses QEMU `-display none`; ARM64 uses `-nographic`. Minecraft client gates continue to use private `xvfb-run -a` displays.

## Persistent state

The Proxmox host retains only the prepared guest state:

| Guest | Host state directory | Reused credential |
| --- | --- | --- |
| Windows 11 x86-64 | `/var/lib/cinemarr-hwtest/windows-x86_64` | In-guest startup task; no remote login credential |
| Debian Linux ARM64 | `/var/lib/cinemarr-hwtest/linux-arm64` | Dedicated Ed25519 key under the invoking user's local data directory |

Each directory is mode `0700` and contains `state.json` with an exact platform identity. The runner refuses an absent, malformed, non-ready, or mismatched marker instead of guessing that an arbitrary directory is safe. The ARM private key defaults to `~/.local/share/cinemarr-hwtest/linux-arm64/id_ed25519`, remains outside the repository, and is forced to mode `0600`.

The Windows guest installs an at-startup `CinemarrNativeSmoke` scheduled task during its first acceptance run. On later boots that task waits for the fresh `CINEMARR` payload ISO and `CINEVIDENCE` disk, runs the current benchmark bundle, writes evidence, and powers the guest off. The ARM guest retains its cloud image overlay and Java/native ABI packages; each run transfers the current bundle over its dedicated SSH identity and powers the guest off after evidence retrieval.

## First provisioning and later reuse

Prepare the current decoder bundle first:

```bash
./gradlew --no-daemon prepareDecoderBenchmarkBundle
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

## Failure and reprovisioning

The cleanup trap always stops the uniquely tagged transient unit. A failed first provisioning deliberately leaves `state.json` in `provisioning` state for diagnosis, and a failed run retains its Windows per-run handoff directory. The runner will not delete or overwrite either automatically.

Before reprovisioning, inspect the exact state directory, confirm no QEMU process references its disk, archive any evidence needed for diagnosis, and remove only that platform's validated directory and its ARM key when intentionally rotating the ARM identity. Never use a wildcard or remove `/var/lib/cinemarr-hwtest` as a whole. A Windows installer refresh or ARM base-image refresh is an explicit reprovisioning event, not part of an ordinary rerun.
