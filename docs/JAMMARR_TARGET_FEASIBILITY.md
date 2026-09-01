# Jammarr target feasibility for Cinemarr

This assessment compares Cinemarr with Jammarr commit
`6d4429bcb972a832198ac78b0e1a0b9030c22e3f` and its 1.1.0 target manifest.
That manifest contains 78 implemented artifacts across 29 Minecraft versions
and six loader IDs, mapped to 99 dedicated-server runtime profiles. Twenty-two
of those artifacts are still marked preview, and three LiteLoader artifacts
are client companions rather than standalone server/client loader targets.
Those distinctions must not be lost when using Jammarr as design input.

Cinemarr 1.0 remains fixed at 16 artifacts and 21 certified runtimes. Target
expansion is post-1.0 work and must not delay or weaken the current release
gates.

## Already covered

Cinemarr already covers the following Jammarr boundaries with required clients
and full television/video behavior:

- Forge 1.7.10.
- Fabric, Forge, and NeoForge 1.20.1 and 1.20.2, with the Fabric artifacts also
  certified on Quilt.
- Fabric, Forge, and NeoForge 1.21.1.
- Fabric, Forge, and NeoForge 26.1.2 and 26.2, with the Fabric artifacts also
  certified on Quilt.

## Feasible additions

Every standalone modern Jammarr target is technically feasible for Cinemarr,
but none is a metadata-only port. Each version/loader boundary must map custom
blocks and block entities, saved data, menus, rendering, required-client
negotiation, video transport, positional audio, JavaCV/FFmpeg packaging, and
clean lifecycle behavior.

The practical implementation order is:

1. Extend the existing modern family adapters to 1.20, 1.20.3 through 1.20.6,
   1.21, and 1.21.2 through 1.21.11 for the loader combinations that actually
   exist in Jammarr. Reuse Fabric artifacts on Quilt only where runtime
   certification passes.
2. Add new architectural bridges for Fabric and Forge 1.16.5, 1.18.2, and
   1.19.2. These are feasible but cross rendering, networking, registry, and
   saved-data API generations that the current families do not cover.
3. Port Forge 1.12.2 and 1.8.9 as full legacy implementations derived from the
   proven 1.7.10 core/protocol contract. Forge 1.6.4 is also feasible if
   Cinemarr explicitly requires Java 8; preserving Java 6/7 client
   compatibility is not realistic with the current decoder stack.
4. Treat Babric beta 1.7.3, legacy Fabric 1.6.4/1.8.9, and Ornithe
   1.6.4/1.8.9 as research-grade full ports. Their loaders can host the needed
   mixins, packets, blocks, rendering, and OpenAL/FFmpeg integration, so they
   are not inherently impossible. They have little reusable Cinemarr platform
   code and therefore carry the highest build-system, mapping, native-runtime,
   and certification cost.

New targets should enter the manifest as implemented-but-uncertified and may
be advertised only after the same two-client video/audio, real-Plex, adverse
network, persistence, cleanup, artifact inspection, and hosted-CI gates used
for the maintained matrix.

Jammarr's deployment work also reinforces three operational requirements for
future Cinemarr expansion:

- Derive server profiles, dependency placement, and evidence totals from the
  target manifest. A single artifact can map to more than one runtime, so
  artifact count is not runtime coverage.
- Make remote deployment read-only by default, verify exact local and remote
  digests, refuse running/autostarted/drifted profiles, retain disabled
  rollback files, and start only one explicitly selected runtime at a time.
- Treat a successful development build as insufficient dependency evidence.
  Jammarr's Legacy Fabric development classpath supplied transitive API
  modules that the production aggregate JAR did not embed. Cinemarr must
  inspect and launch the packaged runtime with its exact external dependency
  set before certifying any legacy target.

The current Jammarr checkout adds two further lessons. First, production
server dependencies must be generated as explicit per-runtime data and tested
on the installed server; its Ornithe and legacy-modern profiles built before
their production dependency placement was corrected. Second, controls that
fit technically are not necessarily understandable: Jammarr now verifies
hover help on both modern and legacy private-display clients. Future Cinemarr
ports should pair small-window clipping probes with screenshot/text evidence
that every non-obvious control has discoverable help. Remote reconciliation
must also audit free space, recover interrupted uploads conservatively, and
leave stopped profiles reusable rather than forcing an expensive reinstall.

A long matrix should be resumable only from sanitized evidence bound to the
exact release version, artifact filename, SHA-256, fresh startup markers, and
clean teardown. Resume must repeat live remote preflight; it must never turn a
stale prior pass into current release evidence.

## Loader boundary that does not transfer

Jammarr's LiteLoader 1.6.4, 1.7.10, and 1.8.9 artifacts are client companions
paired with Forge servers. That works for an audio-only mod whose client can be
optional and which does not add synchronized world registries.

A pure LiteLoader Cinemarr client is not a supportable equivalent. Cinemarr
requires matching server/client protocol negotiation plus registered custom
blocks, items, block entities, resources, menus, and a client renderer. A
LiteLoader-only client cannot satisfy the Forge server's mod registry and
world-content contract. Requiring Forge alongside LiteLoader would make a
separate LiteLoader Cinemarr artifact redundant, while replacing Cinemarr's
world objects with vanilla blocks or a server plugin would be a different
product architecture.

LiteLoader should therefore be recorded as unsupported for Cinemarr rather
than counted toward target parity. Jammarr's optional-client profile likewise
must not be copied: a Cinemarr client that does not decode and render the
television cannot provide the advertised feature.

## Release policy

Jammarr target parity is a direction, not a single release milestone. The
recommended post-1.0 sequence is modern patch coverage, intermediate Java
8/17 families, legacy Forge, and finally alternative legacy loaders. Each
workstream needs its own manifest entries and runtime evidence; artifact count
alone is never a support claim.
