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


def verify_playback_publication(text: str, label: str) -> None:
    normalized = re.sub(r"\s+", "", text)
    if "sessions.applyPlaybackMetadataIfCurrent(prepared.state,System.currentTimeMillis()," not in normalized:
        raise SystemExit(f"{label} must bind metadata to the current playback revision")
    if normalized.count("state=recordPlayback(prepared);if(state==null)return;") != 5:
        raise SystemExit(f"{label} must publish the returned current snapshot in all five completion paths")
    if normalized.count("playbackMetadataGenerations.put(restored.id(),restored.playbackGeneration());") != 2:
        raise SystemExit(f"{label} must bind both restore paths to the restored playback revision")
    if "metadataMatches(state)" not in normalized:
        raise SystemExit(f"{label} must guard checkpoints by playback revision")
    if 'state.paused()?"Paused":"Playing"' in normalized or "state.playbackMessage()" not in normalized:
        raise SystemExit(f"{label} must not describe suspended playback as playing")
    for message in ("Playing next queued video", "Continuing with next episode"):
        call = 'state.playbackMessage("' + re.sub(r"\s+", "", message) + '")'
        if normalized.count(call) != 1:
            raise SystemExit(f"{label} must preserve state-aware contextual playback feedback: {message}")


def verify_stream_identity_transport(text: str, label: str) -> None:
    compact = re.sub(r"\s+", "", text)
    required = (
        "sessions.snapshotIfPresent(identity.timelineId(),identity.timelineGeneration(),System.currentTimeMillis())!=null",
        "tvStreams.isViewer(identity,viewer)",
        "newVideoPackets.SegmentChunk(request.identity(),",
        "newVideoPackets.SegmentManifest(identity,",
        "newTransferGrantRegistry(30_000L,CinemarrSettings.maximumConcurrentStreams())",
        "transferGrants.expireWindows(now)",
        "transferGrants.releaseExcept(playerId,trackedStreams)",
        "egress.removeMatching(client,",
        "this::sendCurrentSegment",
        "tickTelevisionStreams(now);pruneRetiredTransfers();",
        "newVideoHealthRegistry(30_000L,CinemarrSettings.maximumConcurrentStreams())",
        "clientHealth.retain(playerId,trackedStreams)",
        "clientHealth.prune(now,this::isCurrentViewer)",
        "clientHealth.currentReports(now,this::isCurrentViewer)",
    )
    for contract in required:
        if contract not in compact:
            raise SystemExit(f"{label} lacks shared-timeline/TV-stream transport ownership: {contract}")
    health = compact[compact.index("publicvoidhealth("):compact.index("publicStringstatus(")]
    if not re.search(r"clientHealth\.record\(player\.get(?:UUID|UniqueID)\(\),value,System\.currentTimeMillis\(\),this::isCurrentViewer\)", health):
        raise SystemExit(f"{label} must validate health against both identities before recording")
    segments = compact[compact.index("publicvoidsegments("):compact.index("publicvoidmanifest(")]
    completion = segments[segments.index(".whenComplete("):]
    guard = "!isCurrentViewer(request.identity(),"
    if guard not in completion or completion.index(guard) > completion.index("if(failure!=null)"):
        raise SystemExit(f"{label} must reject obsolete media completion before reporting failures")
    acknowledgement = compact[compact.index("publicvoidacknowledge("):compact.index("privatebooleanisCurrentViewer(")]
    guard = "!isCurrentViewer(value.identity(),"
    if guard not in acknowledgement or acknowledgement.index(guard) > acknowledgement.index("transferGrants.acknowledge("):
        raise SystemExit(f"{label} must validate both identities before releasing an acknowledged window")
    if "tvStreams.isSupersededViewer(" not in compact or "privatevoidtransportError(" not in compact:
        raise SystemExit(f"{label} must distinguish stale transport feedback from ownership errors")


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
        audio = re.sub(r"\s+", "", (base / (prefix + "VideoAudio.java")).read_text("utf-8"))
        for contract in ("privateVideoStreamIdentityidentity;", "bindIdentity(session.identity());",
                         "if(!next.equals(identity)){reset();identity=next;}", "pending.clear();identity=null;"):
            if contract not in audio:
                raise SystemExit(f"{prefix} audio must bind and reset the full timeline/stream identity: {contract}")
        if prefix == "Cinemarr" and ("VideoStreamIdentityexpectedIdentity=identity;" not in audio
                                    or "!expectedIdentity.equals(identity)" not in audio):
            raise SystemExit("Modern asynchronous audio start must validate the full stream identity")
        playback = (base / (prefix + "VideoPlayback.java")).read_text("utf-8")
        manager = (base / (prefix + "VideoPlaybackManager.java")).read_text("utf-8")
        if "PausedFrameRetention.permits(previous.identity,previous.itemKey,next)" not in re.sub(r"\s+", "", playback) or "previous.texture = new " not in playback:
            raise SystemExit(f"{prefix} paused frame must have a single, policy-guarded texture owner")
        if "state.stream(previous.getKey())" not in manager or "retainPausedFrameFrom(" not in manager:
            raise SystemExit(f"{prefix} paused frame transfer must not steal a still-referenced texture")
        if "retainReplacementFrameFrom(" not in manager:
            raise SystemExit(f"{prefix} quality replacement must retain the last frame for the same TV")
        if "previous.televisionId" not in playback or "previous.identity.timelineGeneration()" not in playback:
            raise SystemExit(f"{prefix} replacement retention must be scoped to the TV and shared timeline")
            raise SystemExit(f"{prefix} paused frame transfer must not steal a still-referenced texture")
    for source in ACCEPTANCE_VIDEO_SCREENS:
        if source.name == "LegacyVideoScreen.java":
            continue
        text = source.read_text("utf-8")
        if '"display-settings"' not in text:
            raise SystemExit(f"{source.relative_to(ROOT)} lacks the Display Settings entry point")
        body = text[text.index("public final class CinemarrVideoScreen"):
                    text.index("    @Override public boolean keyPressed")]
        body = body.replace("private int acceptanceScreenshotTicks;",
                            "private boolean acceptanceScreenshotPending;")
        body = body.replace("acceptanceScreenshotTicks=2;", "acceptanceScreenshotPending=true;")
        body = re.sub(r'widget\("display-settings",Button\.builder\(Component\.literal\("Display"\).*?build\(\)\);', "", body)
        body = re.sub(r"\s+", "", body)
        # Minecraft GUI signatures differ by version; normalize those narrow
        # adapter shims before comparing shared browse/control behavior.
        body = body.replace("renderBackground(graphics);", "renderBackground(graphics,mouseX,mouseY,partial);")
        body = body.replace("renderBackground(graphics,mouseX,mouseY,partial);", "renderBackground(graphics,mouseX,mouseY,partial);")
        body = body.replace("mouseScrolled(double mouseX,double mouseY,double scrollY)", "mouseScrolled(double mouseX,double mouseY,double scrollX,double scrollY)")
        body = body.replace("super.mouseScrolled(mouseX,mouseY,scrollY)", "super.mouseScrolled(mouseX,mouseY,scrollX,scrollY)")
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
        verify_stream_identity_transport(text, str(source.relative_to(ROOT)))
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
        if "VideoHealthRegistry.Result.INVALID" not in text or "clientHealth.put(" in text:
            raise SystemExit(f"{source.relative_to(ROOT)} must validate and retain health through the bounded stream registry")
        normalized = re.sub(r"\s+", "", text)
        if "newTelevisionStreamPool(CinemarrSettings.maximumConcurrentStreams()," not in normalized:
            raise SystemExit(f"{source.relative_to(ROOT)} must apply the configured stream cap to TV media ownership")
        if "this::startTelevisionMedia,operation->workers.supply(operation)" not in normalized:
            raise SystemExit(f"{source.relative_to(ROOT)} must submit TV starts through the bounded work queue")
        verify_playback_publication(text, str(source.relative_to(ROOT)))
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
