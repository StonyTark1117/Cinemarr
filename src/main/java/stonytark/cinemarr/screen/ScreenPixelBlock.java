package stonytark.cinemarr.screen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/** Ordinary, non-ticking screen pixel; membership is maintained in world saved data. */
public final class ScreenPixelBlock extends DirectionalBlock {
    public static final com.mojang.serialization.MapCodec<ScreenPixelBlock> CODEC = simpleCodec(ScreenPixelBlock::new);
    public ScreenPixelBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override protected com.mojang.serialization.MapCodec<? extends DirectionalBlock> codec() { return CODEC; }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getNearestLookingDirection().getOpposite());
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide && level instanceof ServerLevel && !oldState.is(state.getBlock())) {
            CinemarrWorldScreens.get((ServerLevel) level).putPixel(pos, state.getValue(FACING));
        }
    }

    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!level.isClientSide && level instanceof ServerLevel && !newState.is(state.getBlock())) {
            CinemarrWorldScreens.get((ServerLevel) level).removePixel(pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
