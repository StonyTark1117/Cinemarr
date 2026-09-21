package stonytark.cinemarr.server;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import stonytark.cinemarr.core.protocol.ProtocolLimits;
import stonytark.cinemarr.core.protocol.VideoPackets;
import stonytark.cinemarr.core.video.*;
import stonytark.cinemarr.registry.CinemarrBlocks;
import stonytark.cinemarr.screen.CinemarrWorldScreens;
import stonytark.cinemarr.screen.ScreenPixelBlock;

/** Opt-in disposable acceptance scene, using the ordinary registration and command paths. */
public final class AcceptanceDisplaySetup {
    public static void prepare(ServerLevel level, ServerPlayer player, ServerVideoManager manager) {
        if (!ProtocolLimits.displayProbeEnabled()) return;
        CinemarrWorldScreens screens = CinemarrWorldScreens.get(level);
        CinemarrWorldScreens.Television quick = screens.television(new BlockPos(-1, 100, -1));
        if (quick == null || !quick.owner().equals(player.getUUID())) return;
        for (int index = 0; index < 2; index++) {
            int x = index == 0 ? -7 : 4;
            BlockPos controller = new BlockPos(x, 110, 3);
            if (screens.television(controller) == null) {
                for (int dx = 0; dx < 4; dx++) for (int dy = 0; dy < 4; dy++) {
                    BlockPos pixel = new BlockPos(x + dx, 110 + dy, 2);
                    level.setBlockAndUpdate(pixel, CinemarrBlocks.screenPixel().defaultBlockState().setValue(ScreenPixelBlock.FACING, Direction.SOUTH));
                    screens.putPixel(pixel, Direction.SOUTH);
                }
                level.setBlockAndUpdate(controller, CinemarrBlocks.tvController().defaultBlockState());
                if (!screens.activate(controller, player.getUUID()).success()) throw new IllegalStateException("Custom TV acceptance activation failed");
                TvDisplaySettings old = screens.television(controller).displaySettings();
                screens.updateDisplay(controller, new TvDisplaySettings(old.origin(), PresentationMode.FIT,
                        index == 0 ? PixelMapping.ONE_PIXEL_PER_BLOCK : PixelMapping.DETAILED,
                        ResolutionChoice.preset(index == 0 ? "144p" : "480p"), old.revision()));
            }
            manager.command(player, new VideoPackets.SessionCommand(VideoPackets.SessionAction.TUNE, controller.asLong(),
                    "", "", "cinemarr-acceptance", PresentationMode.FIT, 0, 0, -1, -1));
        }
    }
    private AcceptanceDisplaySetup() {}
}
