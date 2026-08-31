package stonytark.cinemarr.screen;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import stonytark.cinemarr.network.CinemarrNetwork;
import stonytark.cinemarr.network.VideoPayloads;

public final class TvControllerBlock extends Block {
    public TvControllerBlock(BlockBehaviour.Properties properties){super(properties);}
    @Override public InteractionResult use(BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){if(level.isClientSide)return InteractionResult.SUCCESS;CinemarrWorldScreens.Activation result=CinemarrWorldScreens.get((ServerLevel)level).activate(pos,player.getUUID());player.displayClientMessage(Component.literal(result.message()),false);if(result.success()&&player instanceof ServerPlayer)CinemarrNetwork.sendToPlayer((ServerPlayer)player,new VideoPayloads.OpenVideoScreen(pos.asLong()));return result.success()?InteractionResult.SUCCESS:InteractionResult.FAIL;}
    @Override public void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moved){if(!level.isClientSide&&level instanceof ServerLevel&&!next.is(state.getBlock()))CinemarrWorldScreens.get((ServerLevel)level).removeController(pos);super.onRemove(state,level,pos,next,moved);}
}
