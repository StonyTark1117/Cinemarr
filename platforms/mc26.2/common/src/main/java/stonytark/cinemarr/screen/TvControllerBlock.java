package stonytark.cinemarr.screen;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import stonytark.cinemarr.network.CinemarrNetwork;
import stonytark.cinemarr.network.VideoPayloads;

/** Activates or refreshes an adjacent recorded pixel silhouette. */
public final class TvControllerBlock extends Block {
    public TvControllerBlock(BlockBehaviour.Properties properties) { super(properties); }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                                         BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        CinemarrWorldScreens.Activation result = CinemarrWorldScreens.get((ServerLevel) level).activate(pos, player.getUUID());
        player.sendSystemMessage(Component.literal(result.message()));
        if(result.success()&&player instanceof ServerPlayer serverPlayer){
            CinemarrNetwork.sendToPlayer(serverPlayer,new VideoPayloads.OpenVideoScreen(pos.asLong()));
        }
        return result.success() ? InteractionResult.SUCCESS : InteractionResult.FAIL;
    }

    @Override protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        CinemarrWorldScreens.get(level).removeController(pos);
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
    }
}
