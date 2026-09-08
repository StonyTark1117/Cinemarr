#!/usr/bin/env python3
"""Reject copied platform sources that should live in a family-shared source set."""

from __future__ import annotations

import hashlib
import json
import re
from collections import defaultdict
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
VERSION_COMMON = (
    ROOT / "platforms/mc1.20.1/common/src/main/java",
    ROOT / "platforms/mc1.20.2/common/src/main/java",
    ROOT / "platforms/mc26.1.2/common/src/main/java",
    ROOT / "platforms/mc26.2/common/src/main/java",
)
FAMILY_REFERENCES = {
    "mc1.20": ROOT / "platforms/mc1.20/common/src/main/java",
    "mc26": ROOT / "platforms/mc26/common/src/main/java",
}
ACCEPTANCE_VIDEO_SCREENS = (
    ROOT / "src/main/java/stonytark/cinemarr/client/CinemarrVideoScreen.java",
    ROOT / "platforms/mc1.20.1/common/src/main/java/stonytark/cinemarr/client/CinemarrVideoScreen.java",
    ROOT / "platforms/mc26/common/src/main/java/stonytark/cinemarr/client/CinemarrVideoScreen.java",
    ROOT / "platforms/mc1.7.10/forge/src/main/java/stonytark/cinemarr/client/LegacyVideoScreen.java",
)
MC26_UI_CAPTURES = {
    ROOT / "platforms/mc26.1.2/common/src/main/java/stonytark/cinemarr/client/CinemarrVideoUiCapture.java":
        "minecraft.getMainRenderTarget()",
    ROOT / "platforms/mc26.2/common/src/main/java/stonytark/cinemarr/client/CinemarrVideoUiCapture.java":
        "minecraft.gameRenderer.mainRenderTarget()",
}


def main() -> None:
    for path in ("src/main/java/stonytark/cinemarr/server/ServerVideoManager.java",
                 "platforms/mc26/common/src/main/java/stonytark/cinemarr/server/ServerVideoManager.java",
                 "platforms/mc1.7.10/forge/src/main/java/stonytark/cinemarr/server/LegacyVideoManager.java"):
        source = (ROOT / path).read_text("utf-8")
        normalized = re.sub(r"\s+", "", source).replace("1024L", "1024")
        if "(64,2L*1024*1024,256,8L*1024*1024,1024,16L*1024*1024)" not in normalized:
            raise SystemExit("server egress byte budgets drifted: " + path)
    for path in ("src/main/java/stonytark/cinemarr/client/FfmpegVideoDecoder.java",
                 "platforms/mc1.7.10/forge/src/main/java/stonytark/cinemarr/client/LegacyFfmpegVideoDecoder.java"):
        source = (ROOT / path).read_text("utf-8")
        for contract in ("DecodedBufferBudget audioBudget = new DecodedBufferBudget()",
                         "ByteBuffer.allocate(budget.reservePcm16(totalSamples))",
                         "long totalSamples = 0", "DecodedBufferBudget.rgbaBytes(width, height)",
                         "while (nextFrameAllowed() &&", "DecodedBufferBudget.checkCancelled()"):
            if contract not in source:
                raise SystemExit("decoder lacks pre-allocation budget/cancellation: " + path + ": " + contract)
    legacy_network = (ROOT / "platforms/mc1.7.10/forge/src/main/java/stonytark/cinemarr/network/LegacyNetwork.java").read_text("utf-8")
    if "ConcurrentLinkedQueue" in legacy_network:
        raise SystemExit("legacy inbound transport must not retain an unbounded handoff queue")
    for contract in ("BoundedPacketInbox<NetworkManager, ServerIncoming>",
                     "BoundedPacketInbox<NetworkManager, ClientIncoming>",
                     "SERVER_INBOX.drain(MAX_INCOMING_PER_TICK,", "CLIENT_INBOX.drain(MAX_INCOMING_PER_TICK,",
                     "SERVER_INBOX.remove(connection)", "CLIENT_INBOX.remove(event.manager)",
                     "envelope.payloadLength()", "incoming.connection == clientConnection",
                     "HelloGate<NetworkManager>", "if (!helloGate.accept(connection)) return;",
                     "helloGate.require(connection,", "helloGate.remove(connection)", "helloGate.expire(now)"):
        if contract not in legacy_network:
            raise SystemExit("legacy inbound transport lacks bounded connection ownership: " + contract)
    hashes: dict[str, list[Path]] = defaultdict(list)
    for directory in VERSION_COMMON:
        if not directory.is_dir():
            continue
        for source in directory.rglob("*.java"):
            hashes[hashlib.sha256(source.read_bytes()).hexdigest()].append(source)
    duplicates = [paths for paths in hashes.values() if len(paths) > 1]
    if duplicates:
        rendered = "; ".join(", ".join(str(path.relative_to(ROOT)) for path in paths)
                             for paths in duplicates)
        raise SystemExit(f"identical version-specific Java sources must be family-shared: {rendered}")

    for family, directory in FAMILY_REFERENCES.items():
        if not any(directory.rglob("*.java")):
            raise SystemExit(f"family source set is empty: {directory.relative_to(ROOT)}")
        expected = f"../../{family}/common/src/main"
        versions = ("mc1.20.1", "mc1.20.2") if family == "mc1.20" else ("mc26.1.2", "mc26.2")
        for version in versions:
            for loader in ("fabric", "forge", "neoforge"):
                build = ROOT / f"platforms/{version}/{loader}/build.gradle"
                if expected not in build.read_text("utf-8"):
                    raise SystemExit(f"{build.relative_to(ROOT)} does not include {family} shared sources")

    for source in ACCEPTANCE_VIDEO_SCREENS:
        text = source.read_text("utf-8")
        if "Acceptance video UI:" not in text or "clipped=" not in text or "canControl=" not in text:
            raise SystemExit(f"{source.relative_to(ROOT)} lacks release UI acceptance instrumentation")
        compact = text.replace(" ", "")
        for contract in ("newVideoControllerFeedback()", "feedback.updateServerMessage(",
                         "feedback.request(", "feedback.error(", "feedback.message()",
                         "Acceptancevideowidgetcommand:", "Acceptancevideocontrollererrordisplayed"):
            if contract not in compact:
                raise SystemExit(f"{source.relative_to(ROOT)} lacks guarded controller feedback: {contract}")
        if compact.count("state.command(") != 1 or 'notice="Starting' in compact or 'notice="Queued' in compact:
            raise SystemExit(f"{source.relative_to(ROOT)} must have one guarded widget send point")
        if ".positionMs()" in text or "authoritativePositionMs" not in text:
            raise SystemExit(f"{source.relative_to(ROOT)} controller time/seek must use the synchronized live clock")
        if "width/2,layout.noticeY(),0xffffb36b" not in compact:
            raise SystemExit(f"{source.relative_to(ROOT)} must reserve a notice line above stream controls")
        for contract in ("newVideoControllerLayout(width,height)", "layout.rows(false)", "layout.rows(true)",
                         "layout.libraryCapacity()", "results.query().equals(query)", "results.page()!=page", "overlaps={}"):
            if contract not in compact:
                raise SystemExit(f"{source.relative_to(ROOT)} lacks shared scaled-layout/browse contract: {contract}")
        if "0xffffffff" not in text or "0xffa0d8ff" not in text:
            raise SystemExit(f"{source.relative_to(ROOT)} title and playback text require opaque ARGB colors")
        if source.name == "LegacyVideoScreen.java":
            if "new LegacyVideoButton(" not in text:
                raise SystemExit("legacy wide title/library buttons must use bounded texture slices")
            if "button.id==NEXT_PAGE" not in compact or "button.id==PREVIOUS_PAGE" not in compact:
                raise SystemExit("legacy browser must expose both server-page actions")
        elif any(contract not in compact for contract in (
                "control(Slot.CONTINUE,", "search=edit(search,", "super.rebuildWidgets();setFocused(",
                "field.setHint(Component.literal(trim(")):
            raise SystemExit(f"{source.relative_to(ROOT)} must share episode geometry and retain search widgets")
    for source in [ROOT / "src/main/java/stonytark/cinemarr/client/CinemarrClientState.java",
                   ROOT / "platforms/mc1.7.10/forge/src/main/java/stonytark/cinemarr/client/LegacyClientState.java"]:
        text = source.read_text("utf-8")
        opening = text.split('if ("video:open-ui".equals(operation)) {', 1)[1].split('return;\n        }', 1)[0]
        if ".requestLibraries();" not in opening:
            raise SystemExit(f"{source.relative_to(ROOT)} acceptance UI must request libraries like a real controller open")
    modern_body = None
    for prefix, base in (("Cinemarr", ROOT / "src/main/java/stonytark/cinemarr/client"),
                         ("Legacy", ROOT / "platforms/mc1.7.10/forge/src/main/java/stonytark/cinemarr/client")):
        playback = (base / (prefix + "VideoPlayback.java")).read_text("utf-8")
        manager = (base / (prefix + "VideoPlaybackManager.java")).read_text("utf-8")
        if "PausedFrameRetention.permits(" not in playback or "previous.texture = new " not in playback:
            raise SystemExit(f"{prefix} paused frame must have a single, policy-guarded texture owner")
        if "state.stream(previous.getKey())" not in manager or "retainPausedFrameFrom(" not in manager:
            raise SystemExit(f"{prefix} paused frame transfer must not steal a still-referenced texture")
    for source in ACCEPTANCE_VIDEO_SCREENS:
        if source.name == "LegacyVideoScreen.java":
            continue
        text = source.read_text("utf-8")
        body = text[text.index("public final class CinemarrVideoScreen"):
                    text.index("    @Override public boolean keyPressed")]
        body = body.replace("private int acceptanceScreenshotTicks;",
                            "private boolean acceptanceScreenshotPending;")
        if modern_body is None:
            modern_body = body
        elif body != modern_body:
            raise SystemExit(f"{source.relative_to(ROOT)} modern browse/control behavior diverges from shared policy")
    for source, accessor in MC26_UI_CAPTURES.items():
        if accessor not in source.read_text("utf-8"):
            raise SystemExit(f"{source.relative_to(ROOT)} lacks its remappable render-target accessor")
    audio_reference = (ROOT / "src/main/java/stonytark/cinemarr/client/CinemarrVideoAudio.java").read_text("utf-8")
    for version, camera in (("mc26.1.2", "getMainCamera()"), ("mc26.2", "mainCamera()")):
        source = ROOT / f"platforms/{version}/common/src/main/java/stonytark/cinemarr/client/CinemarrVideoAudio.java"
        normalized = source.read_text("utf-8").replace(
            f"gameRenderer.{camera}.position()", "gameRenderer.getMainCamera().getPosition()")
        if normalized != audio_reference:
            raise SystemExit(f"{source.relative_to(ROOT)} audio clock drift exceeds its camera API shim")
    targets = json.loads((ROOT / "gradle/targets.json").read_text("utf-8"))["artifacts"]
    managers = [ROOT / "src/main/java/stonytark/cinemarr/server/ServerVideoManager.java"]
    managers.extend((ROOT / "platforms").rglob("*VideoManager.java"))
    for source in managers:
        text = source.read_text("utf-8")
        if '"Buffering"' in text:
            raise SystemExit(f"{source.relative_to(ROOT)} must not retain Buffering after completed play/seek")
        if "sessions.reconfigure(tuned.name()," not in text or "playbackOptions.get(state.id())" not in text:
            raise SystemExit(f"{source.relative_to(ROOT)} must preserve the server cursor and paused stream metadata")
        if text.count("sessions.isSupersededViewer(") != 2 or "private void transportError(" not in text:
            raise SystemExit(f"{source.relative_to(ROOT)} must distinguish obsolete viewer traffic from ownership errors")
        segments = text[text.index("    public void segments("):text.index("    public void manifest(")]
        completion = segments[segments.index(".whenComplete("):].replace(" ", "")
        if completion.index("!sessions.isViewer(") > completion.index("if(failure!=null)"):
            raise SystemExit(f"{source.relative_to(ROOT)} must discard obsolete media completion failures before notifying players")
        if source.name == "ServerVideoManager.java":
            if "java.util.function.Predicate<UUID> handshakeComplete" not in text:
                raise SystemExit(f"{source.relative_to(ROOT)} must receive the actual adapter handshake gate")
            refresh = text[text.index("private void refreshTracking(ServerPlayer player) {"):]
            guard = "if (closed || !handshakeComplete.test(player.getUUID())) return;"
            if guard not in refresh or refresh.index(guard) > refresh.index("UUID playerId"):
                raise SystemExit(f"{source.relative_to(ROOT)} must guard tracking before viewer/cache mutation")
            if "for (ServerPlayer player : server.getPlayerList().getPlayers()) synchronizeTrackingRadius(player);" not in text:
                raise SystemExit(f"{source.relative_to(ROOT)} must synchronize accepted players on late Plex installation")
        if "new ActiveVideoMedia(" not in text or "class ActiveMedia" in text or "class ActiveVideoMedia" in text:
            raise SystemExit(f"{source.relative_to(ROOT)} must use the core segment-fetch/cache implementation")
        if "VideoHealthPolicy.classify(" not in text or "VideoHealthPolicy.Decision.IGNORE_STALE" not in text:
            raise SystemExit(f"{source.relative_to(ROOT)} must discard stale health reports through the core policy")
        if "this::startMedia, true)" not in text:
            raise SystemExit(f"{source.relative_to(ROOT)} must use bounded asynchronous media retirement")
        if "sessions.applyIfCurrent(" not in text or "metadataMatches(state)" not in text:
            raise SystemExit(f"{source.relative_to(ROOT)} must guard playback metadata by generation")
        if "tuned.generation()+1" in text.replace(" ", ""):
            raise SystemExit(f"{source.relative_to(ROOT)} must use the actual completed seek generation")
    for target in targets:
        client = "LegacyVideoRuntime" if target["minecraft"] == "1.7.10" else "CinemarrClient"
        client_source = ROOT / target["path"] / f"src/main/java/stonytark/cinemarr/client/{client}.java"
        if "ProtocolLimits.videoProbeViewReady(" not in client_source.read_text("utf-8"):
            raise SystemExit(f"{client_source.relative_to(ROOT)} must reject dead or GUI-obscured video captures")
        lifecycle_source = client_source if target["minecraft"] != "1.7.10" else client_source.with_name("LegacyClient.java")
        lifecycle_text = lifecycle_source.read_text("utf-8")
        if "Acceptance client media reset complete" not in lifecycle_text:
            raise SystemExit(f"{lifecycle_source.relative_to(ROOT)} lacks observed disconnect cleanup")
        if target["minecraft"] != "1.7.10":
            join = "connections.joined(handler, this::helloAfterReset)" if target["loader"] == "fabric" else "connections.joined(event.getConnection(), this::helloAfterReset)"
            disconnect = "connections.disconnected(handler)" if target["loader"] == "fabric" else "connections.disconnected(connection)"
            for contract in ("ClientConnectionLifecycle", join, disconnect,
                             "Minecraft.getInstance().execute(task)", "this::resetConnection",
                             "RenderSystem.assertOnRenderThread()", "Acceptance client JOIN reset complete"):
                if contract not in lifecycle_text:
                    raise SystemExit(f"{lifecycle_source.relative_to(ROOT)} lacks client-thread connection ownership: {contract}")
        name = "LegacyVideoManager" if target["minecraft"] == "1.7.10" else "CinemarrServer"
        source = ROOT / target["path"] / f"src/main/java/stonytark/cinemarr/server/{name}.java"
        server_text = source.read_text("utf-8")
        if target["minecraft"] != "1.7.10":
            gate = "helloGate::accepted" if target["loader"] == "fabric" else "stonytark.cinemarr.core.network.RequiredClientGate::accepted"
            if not re.search(r"CinemarrVideoSavedData.get\(server\),\s*" + re.escape(gate), server_text):
                raise SystemExit(f"{source.relative_to(ROOT)} does not wire its actual handshake gate into tracking")
            hello = server_text[server_text.index("public void hello("):]
            hello = hello[:hello.index("}")]
            if ".synchronizeTrackingRadius(sender)" not in hello:
                raise SystemExit(f"{source.relative_to(ROOT)} must synchronize normal players after hello acceptance")
        if "ProtocolLimits.videoProbeCameraX(" not in server_text:
            raise SystemExit(f"{source.relative_to(ROOT)} lacks reconnect-stable acceptance camera ownership")
    print("family-shared source layout verification passed")


if __name__ == "__main__":
    main()
