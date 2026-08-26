package stonytark.cinemarr.server;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import stonytark.cinemarr.Cinemarr;
import stonytark.cinemarr.core.library.LibraryAllowlistFiles;
import stonytark.cinemarr.core.library.LibraryRule;
import stonytark.cinemarr.core.network.HelloGate;
import stonytark.cinemarr.core.platform.CanonicalConfigFiles;
import stonytark.cinemarr.core.platform.CinemarrSettings;
import stonytark.cinemarr.core.protocol.ProtocolLimits;
import stonytark.cinemarr.core.protocol.VideoPackets;
import stonytark.cinemarr.core.server.PlexVideoService;
import stonytark.cinemarr.network.CinemarrNetwork;
import stonytark.cinemarr.network.CinemarrPayloads;
import stonytark.cinemarr.network.VideoPayloads;
import java.nio.file.Path;
import java.util.*;

public final class CinemarrServer {
    private static final CinemarrServer INSTANCE=new CinemarrServer();private static final long HELLO_TIMEOUT_TICKS=100;
    private final HelloGate<UUID> helloGate=new HelloGate<>(HELLO_TIMEOUT_TICKS);private PlexVideoService videoService;private List<PlexVideoService.ResolvedLibrary> libraries=Collections.emptyList();private ServerVideoManager manager;private long ticks;
    public static CinemarrServer instance(){return INSTANCE;}
    public static void register(){
        ServerLifecycleEvents.SERVER_STARTED.register(server->{CinemarrNetwork.activeServer(server);INSTANCE.ticks=0;INSTANCE.helloGate.clear();try{Path configDirectory=FabricLoader.getInstance().getConfigDir();Path canonical=server.getWorldPath(LevelResource.ROOT).resolve("serverconfig").resolve(CanonicalConfigFiles.SERVER_FILE_NAME);CinemarrSettings.installServer(CanonicalConfigFiles.loadServerForLoader(canonical,configDirectory,"fabric"));Path libraryFile=server.getWorldPath(LevelResource.ROOT).resolve("serverconfig").resolve(LibraryAllowlistFiles.FILE_NAME);List<LibraryRule> rules=LibraryAllowlistFiles.load(libraryFile);if(rules.isEmpty())Cinemarr.LOGGER.warn("Cinemarr has no allowed Plex video libraries; edit {} and restart",libraryFile);else if(CinemarrSettings.plexToken().isBlank())Cinemarr.LOGGER.warn("Cinemarr video libraries are configured, but CINEMARR_PLEX_TOKEN/plexToken is empty");else{INSTANCE.videoService=new PlexVideoService(CinemarrSettings.plexUrl(),CinemarrSettings.plexToken());INSTANCE.libraries=INSTANCE.videoService.resolveLibraries(rules);INSTANCE.manager=new ServerVideoManager(server,INSTANCE.videoService,INSTANCE.libraries,CinemarrVideoSavedData.get(server));Cinemarr.LOGGER.info("Validated {} allowed Plex video libraries",INSTANCE.libraries.size());}}catch(Exception error){throw new IllegalStateException("Unable to initialize Cinemarr",error);}});
        ServerLifecycleEvents.SERVER_STOPPING.register(server->{if(INSTANCE.manager!=null){INSTANCE.manager.close();INSTANCE.manager=null;}INSTANCE.videoService=null;INSTANCE.libraries=Collections.emptyList();INSTANCE.helloGate.clear();CinemarrNetwork.activeServer(null);});
        ServerTickEvents.END_SERVER_TICK.register(server->{INSTANCE.ticks++;for(UUID id:INSTANCE.helloGate.expire(INSTANCE.ticks)){ServerPlayer timedOut=server.getPlayerList().getPlayer(id);if(timedOut!=null)timedOut.connection.disconnect(Component.literal("Cinemarr client handshake timed out; install a compatible Cinemarr protocol "+CinemarrNetwork.PROTOCOL+" client"));}if(INSTANCE.manager!=null){INSTANCE.manager.tick();if(INSTANCE.ticks%10==0)for(ServerPlayer player:server.getPlayerList().getPlayers())INSTANCE.manager.synchronizeTrackingRadius(player);}});
        ServerPlayConnectionEvents.JOIN.register((handler,sender,server)->{if(!net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(handler.player,CinemarrPayloads.ServerHello.ID)){handler.disconnect(Component.literal("Cinemarr is required on the client (protocol "+CinemarrNetwork.PROTOCOL+")"));return;}INSTANCE.helloGate.require(handler.player.getUUID(),INSTANCE.ticks);});
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->{INSTANCE.helloGate.remove(handler.player.getUUID());if(INSTANCE.manager!=null)INSTANCE.manager.playerLeft(handler.player);});
    }
    public void hello(ServerPlayer sender){if(!helloGate.accept(sender.getUUID()))return;CinemarrNetwork.sendToPlayer(sender,new CinemarrPayloads.ServerHello(ProtocolLimits.VERSION,System.currentTimeMillis()));if(manager!=null)manager.synchronizeTrackingRadius(sender);}
    public boolean accepted(ServerPlayer sender){return helloGate.accepted(sender.getUUID());}
    public void videoLibraries(ServerPlayer player){if(accepted(player)&&manager!=null)manager.sendLibraries(player);else CinemarrNetwork.sendToPlayer(player,new VideoPayloads.LibraryList(new VideoPackets.LibraryList(List.of())));}
    public void videoBrowse(ServerPlayer player,VideoPackets.BrowseRequest value){if(accepted(player)&&manager!=null)manager.browse(player,value);}public void videoCommand(ServerPlayer player,VideoPackets.SessionCommand value){if(accepted(player)&&manager!=null)manager.command(player,value);}public void videoSegments(ServerPlayer player,VideoPackets.SegmentRequest value){if(accepted(player)&&manager!=null)manager.segments(player,value);}public void videoManifest(ServerPlayer player,VideoPackets.SegmentManifestRequest value){if(accepted(player)&&manager!=null)manager.manifest(player,value);}public void videoAcknowledge(ServerPlayer player,VideoPackets.SegmentAcknowledgement value){if(accepted(player)&&manager!=null)manager.acknowledge(player,value);}public void videoHealth(ServerPlayer player,VideoPackets.ClientHealth value){if(accepted(player)&&manager!=null)manager.health(player,value);}public String videoStatus(){return manager==null?"Cinemarr video is unavailable":manager.status();}public String videoDiagnostics(){return manager==null?"Plex=unavailable; libraries=0; sessions=0; transcodes=0":manager.diagnostics();}
}
