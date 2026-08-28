package stonytark.cinemarr.screen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import stonytark.cinemarr.core.platform.CinemarrSettings;
import stonytark.cinemarr.core.screen.QuickTvPreset;
import stonytark.cinemarr.core.server.TelevisionLifecycle;
import stonytark.cinemarr.network.CinemarrNetwork;
import stonytark.cinemarr.network.VideoPayloads;
import stonytark.cinemarr.registry.CinemarrBlocks;

/** A safe, dense 16:9 prefab controller with an exact named rendition target. */
public final class QuickTvBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<QuickTvBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            propertiesCodec(), Codec.STRING.fieldOf("preset").forGetter(block -> block.preset.id()))
            .apply(instance, (properties, id) -> new QuickTvBlock(properties, QuickTvPreset.byId(id))));
    private final QuickTvPreset preset;

    public QuickTvBlock(BlockBehaviour.Properties properties, QuickTvPreset preset) {
        super(properties);
        this.preset = preset;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override protected MapCodec<? extends HorizontalDirectionalBlock> codec() { return CODEC; }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && level instanceof ServerLevel && placer instanceof Player) build((ServerLevel) level, pos, state, (Player) placer);
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                                         BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        CinemarrWorldScreens screens = CinemarrWorldScreens.get((ServerLevel) level);
        if (screens.television(pos) == null && !build((ServerLevel) level, pos, state, player)) return InteractionResult.FAIL;
        if (player instanceof ServerPlayer) CinemarrNetwork.sendToPlayer((ServerPlayer) player,
                new VideoPayloads.OpenVideoScreen(pos.asLong()));
        return InteractionResult.SUCCESS;
    }

    private boolean build(ServerLevel level, BlockPos controller, BlockState state, Player player) {
        String problem = preflight(level, controller, state, player);
        if (problem != null) { player.displayClientMessage(Component.literal(problem), false); return false; }
        Direction front = state.getValue(FACING);
        Direction across = front.getClockWise();
        BlockPos anchor = controller.relative(front).relative(across, -(preset.physicalWidth() / 2));
        BlockState pixel = CinemarrBlocks.screenPixel().defaultBlockState().setValue(ScreenPixelBlock.FACING, front);
        java.util.List<BlockPos> placed = new java.util.ArrayList<>();
        for (int y = 0; y < preset.physicalHeight(); y++) for (int x = 0; x < preset.physicalWidth(); x++) {
            BlockPos target=anchor.relative(across, x).above(y);
            if(level.getBlockState(target).isAir())placed.add(target);
            level.setBlock(target, pixel, 3);
        }
        CinemarrWorldScreens screens = CinemarrWorldScreens.get(level);
        CinemarrWorldScreens.Activation activated = screens.activate(controller, player.getUUID());
        if (!activated.success()) {
            for (int index = placed.size() - 1; index >= 0; index--) {
                BlockPos target = placed.get(index);
                if (level.getBlockState(target).is(CinemarrBlocks.screenPixel())) {
                    level.setBlockAndUpdate(target, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
                }
            }
            player.displayClientMessage(Component.literal(activated.message()), false); return false;
        }
        screens.updateRendition(controller, preset.renditionWidth(), preset.renditionHeight());
        player.displayClientMessage(Component.literal("Built " + preset.id() + " Quick TV: "
                + preset.physicalWidth() + "x" + preset.physicalHeight() + " dense screen, "
                + preset.renditionWidth() + "x" + preset.renditionHeight() + " rendition target"), false);
        return true;
    }

    private String preflight(ServerLevel level, BlockPos controller, BlockState state, Player player) {
        if (!CinemarrSettings.quickTvPresetEnabled(preset)) return preset.id() + " Quick TV kits are disabled by the server";
        CinemarrWorldScreens screens=CinemarrWorldScreens.get(level);
        if(screens.television(controller)==null&&TelevisionLifecycle.count(player.getUUID())>=CinemarrSettings.maximumScreensPerOwner())return "Maximum screens per owner reached";
        if (preset.physicalPixels() < CinemarrSettings.minimumScreenPixels()
                || preset.physicalPixels() > CinemarrSettings.maximumScreenPixels()
                || preset.physicalWidth() > CinemarrSettings.maximumScreenDimension()
                || preset.physicalHeight() > CinemarrSettings.maximumScreenDimension()) {
            return preset.id() + " Quick TV exceeds this server's screen construction limits";
        }
        Direction front = state.getValue(FACING);
        Direction across = front.getClockWise();
        BlockPos anchor = controller.relative(front).relative(across, -(preset.physicalWidth() / 2));
        java.util.Set<Long> footprint=new java.util.HashSet<>();
        for (int y = 0; y < preset.physicalHeight(); y++) for (int x = 0; x < preset.physicalWidth(); x++) {
            BlockPos target = anchor.relative(across, x).above(y);
            footprint.add(target.asLong());
            if (!level.hasChunkAt(target)) return "Load the complete " + preset.id() + " Quick TV footprint before building";
            BlockState existing = level.getBlockState(target);
            if (!existing.isAir() && !existing.is(CinemarrBlocks.screenPixel())) {
                return "The " + preset.id() + " Quick TV footprint is obstructed at " + target.toShortString();
            }
        }
        if(screens.overlaps(controller,footprint))return "Quick TV pixels already belong to another TV";
        return null;
    }


    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!level.isClientSide && level instanceof ServerLevel && !newState.is(state.getBlock())) {
            CinemarrWorldScreens.get((ServerLevel) level).removeController(pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

}
