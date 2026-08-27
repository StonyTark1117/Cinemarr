package stonytark.cinemarr.screen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
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
import stonytark.cinemarr.network.CinemarrNetwork;
import stonytark.cinemarr.network.VideoPayloads;
import stonytark.cinemarr.registry.CinemarrBlocks;

public final class QuickTvBlock extends HorizontalDirectionalBlock {
    private final QuickTvPreset preset;
    public QuickTvBlock(BlockBehaviour.Properties properties, QuickTvPreset preset){super(properties);this.preset=preset;registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH));}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){return defaultBlockState().setValue(FACING,context.getHorizontalDirection().getOpposite());}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block,BlockState> builder){builder.add(FACING);}
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,LivingEntity placer,ItemStack stack){super.setPlacedBy(level,pos,state,placer,stack);if(!level.isClientSide&&level instanceof ServerLevel&&placer instanceof Player)build((ServerLevel)level,pos,state,(Player)placer);}
    @Override public InteractionResult use(BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){if(level.isClientSide)return InteractionResult.SUCCESS;CinemarrWorldScreens screens=CinemarrWorldScreens.get((ServerLevel)level);if(screens.television(pos)==null&&!build((ServerLevel)level,pos,state,player))return InteractionResult.FAIL;if(player instanceof ServerPlayer)CinemarrNetwork.sendToPlayer((ServerPlayer)player,new VideoPayloads.OpenVideoScreen(pos.asLong()));return InteractionResult.SUCCESS;}
    private boolean build(ServerLevel level,BlockPos controller,BlockState state,Player player){String problem=preflight(level,controller,state);if(problem!=null){player.displayClientMessage(Component.literal(problem),false);return false;}Direction front=state.getValue(FACING),across=front.getClockWise();BlockPos anchor=controller.relative(front).relative(across,-(preset.physicalWidth()/2));BlockState pixel=CinemarrBlocks.screenPixel().defaultBlockState().setValue(ScreenPixelBlock.FACING,front);for(int y=0;y<preset.physicalHeight();y++)for(int x=0;x<preset.physicalWidth();x++)level.setBlock(anchor.relative(across,x).above(y),pixel,3);CinemarrWorldScreens screens=CinemarrWorldScreens.get(level);CinemarrWorldScreens.Activation activated=screens.activate(controller,player.getUUID());if(!activated.success()){player.displayClientMessage(Component.literal(activated.message()),false);return false;}screens.updateRendition(controller,preset.renditionWidth(),preset.renditionHeight());player.displayClientMessage(Component.literal("Built "+preset.id()+" Quick TV: "+preset.physicalWidth()+"x"+preset.physicalHeight()+" dense screen, "+preset.renditionWidth()+"x"+preset.renditionHeight()+" rendition target"),false);return true;}
    private String preflight(ServerLevel level,BlockPos controller,BlockState state){if(!CinemarrSettings.quickTvPresetEnabled(preset))return preset.id()+" Quick TV kits are disabled by the server";if(preset.physicalPixels()<CinemarrSettings.minimumScreenPixels()||preset.physicalPixels()>CinemarrSettings.maximumScreenPixels()||preset.physicalWidth()>CinemarrSettings.maximumScreenDimension()||preset.physicalHeight()>CinemarrSettings.maximumScreenDimension())return preset.id()+" Quick TV exceeds this server's screen construction limits";Direction front=state.getValue(FACING),across=front.getClockWise();BlockPos anchor=controller.relative(front).relative(across,-(preset.physicalWidth()/2));for(int y=0;y<preset.physicalHeight();y++)for(int x=0;x<preset.physicalWidth();x++){BlockPos target=anchor.relative(across,x).above(y);if(!level.hasChunkAt(target))return "Load the complete "+preset.id()+" Quick TV footprint before building";BlockState existing=level.getBlockState(target);if(!existing.isAir()&&!existing.is(CinemarrBlocks.screenPixel()))return "The "+preset.id()+" Quick TV footprint is obstructed at "+target.toShortString();}return null;}
    @Override public void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moved){if(!level.isClientSide&&level instanceof ServerLevel&&!next.is(state.getBlock()))CinemarrWorldScreens.get((ServerLevel)level).removeController(pos);super.onRemove(state,level,pos,next,moved);}
}

