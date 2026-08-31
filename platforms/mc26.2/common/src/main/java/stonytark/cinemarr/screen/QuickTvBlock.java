package stonytark.cinemarr.screen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
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

public final class QuickTvBlock extends HorizontalDirectionalBlock {
    private static final int BLOCKS_PER_TICK=256;
    private static final java.util.Map<ServerLevel,java.util.Map<Long,BuildJob>> JOBS=java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
    public static final MapCodec<QuickTvBlock> CODEC=RecordCodecBuilder.mapCodec(instance->instance.group(propertiesCodec(),Codec.STRING.fieldOf("preset").forGetter(block->block.preset.id())).apply(instance,(properties,id)->new QuickTvBlock(properties,QuickTvPreset.byId(id))));
    private final QuickTvPreset preset;
    public QuickTvBlock(BlockBehaviour.Properties properties,QuickTvPreset preset){super(properties);this.preset=preset;registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH));}
    @Override protected MapCodec<? extends HorizontalDirectionalBlock> codec(){return CODEC;}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){return defaultBlockState().setValue(FACING,context.getHorizontalDirection().getOpposite());}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block,BlockState> builder){builder.add(FACING);}
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,LivingEntity placer,ItemStack stack){super.setPlacedBy(level,pos,state,placer,stack);if(!level.isClientSide()&&level instanceof ServerLevel&&placer instanceof Player)build((ServerLevel)level,pos,state,(Player)placer);}
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){if(level.isClientSide())return InteractionResult.SUCCESS;CinemarrWorldScreens screens=CinemarrWorldScreens.get((ServerLevel)level);if(screens.television(pos)==null&&!build((ServerLevel)level,pos,state,player))return InteractionResult.FAIL;if(player instanceof ServerPlayer serverPlayer)CinemarrNetwork.sendToPlayer(serverPlayer,new VideoPayloads.OpenVideoScreen(pos.asLong()));return InteractionResult.SUCCESS;}
    private boolean build(ServerLevel level,BlockPos controller,BlockState state,Player player){String problem=preflight(level,controller,state,player);if(problem!=null){player.sendSystemMessage(Component.literal(problem));return false;}java.util.Map<Long,BuildJob> jobs=jobs(level);if(jobs.containsKey(controller.asLong())){player.sendSystemMessage(Component.literal(preset.id()+" Quick TV construction is already in progress"));return true;}Direction front=state.getValue(FACING),across=front.getClockWise();BlockPos anchor=controller.relative(front).relative(across,-(preset.physicalWidth()/2));java.util.List<BlockPos> targets=new java.util.ArrayList<>(preset.physicalPixels());for(int y=0;y<preset.physicalHeight();y++)for(int x=0;x<preset.physicalWidth();x++)targets.add(anchor.relative(across,x).above(y));CinemarrWorldScreens.get(level).beginQuickTvConstruction(controller,targets);jobs.put(controller.asLong(),new BuildJob(player.getUUID(),front,targets));if(targets.size()<=BLOCKS_PER_TICK){tick(state,level,controller,level.getRandom());return CinemarrWorldScreens.get(level).television(controller)!=null;}level.scheduleTick(controller,this,1);player.sendSystemMessage(Component.literal("Building "+preset.id()+" Quick TV in bounded 256-block batches"));return true;}
    @Override protected void tick(BlockState state,ServerLevel level,BlockPos controller,RandomSource random){BuildJob job=jobs(level).get(controller.asLong());if(job==null)return;if(!state.is(this)){rollback(level,controller,job);return;}BlockState pixel=CinemarrBlocks.screenPixel().defaultBlockState().setValue(ScreenPixelBlock.FACING,job.front);int end=Math.min(job.targets.size(),job.index+BLOCKS_PER_TICK);for(;job.index<end;job.index++){BlockPos target=job.targets.get(job.index);if(!level.hasChunkAt(target)||!level.getBlockState(target).isAir()){rollback(level,controller,job);message(level,job.owner,"Quick TV construction rolled back because its footprint changed or unloaded");return;}level.setBlock(target,pixel,3);job.placed.add(target);}if(job.index<job.targets.size()){level.scheduleTick(controller,this,1);return;}jobs(level).remove(controller.asLong());CinemarrWorldScreens screens=CinemarrWorldScreens.get(level);CinemarrWorldScreens.Activation activated=screens.activate(controller,job.owner);if(!activated.success()){screens.finishQuickTvConstruction(controller);rollbackPlaced(level,job);message(level,job.owner,activated.message());return;}screens.updateRendition(controller,preset.renditionWidth(),preset.renditionHeight());screens.finishQuickTvConstruction(controller);message(level,job.owner,"Built "+preset.id()+" Quick TV: "+preset.physicalWidth()+"x"+preset.physicalHeight()+" screen, "+preset.renditionWidth()+"x"+preset.renditionHeight()+" rendition target");}
    private static java.util.Map<Long,BuildJob> jobs(ServerLevel level){synchronized(JOBS){return JOBS.computeIfAbsent(level,ignored->new java.util.HashMap<>());}}
    private static void rollback(ServerLevel level,BlockPos controller,BuildJob job){jobs(level).remove(controller.asLong());CinemarrWorldScreens.get(level).finishQuickTvConstruction(controller);rollbackPlaced(level,job);}
    private static void rollbackPlaced(ServerLevel level,BuildJob job){for(int i=job.placed.size()-1;i>=0;i--){BlockPos target=job.placed.get(i);if(level.getBlockState(target).is(CinemarrBlocks.screenPixel()))level.setBlockAndUpdate(target,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());}}
    private static void message(ServerLevel level,java.util.UUID owner,String text){ServerPlayer player=level.getServer().getPlayerList().getPlayer(owner);if(player!=null)player.sendSystemMessage(Component.literal(text));}
    private static final class BuildJob{final java.util.UUID owner;final Direction front;final java.util.List<BlockPos> targets,placed=new java.util.ArrayList<>();int index;BuildJob(java.util.UUID owner,Direction front,java.util.List<BlockPos> targets){this.owner=owner;this.front=front;this.targets=targets;}}
    private String preflight(ServerLevel level,BlockPos controller,BlockState state,Player player){if(!CinemarrSettings.quickTvPresetEnabled(preset))return preset.id()+" Quick TV kits are disabled by the server";CinemarrWorldScreens screens=CinemarrWorldScreens.get(level);if(screens.television(controller)==null&&TelevisionLifecycle.count(player.getUUID())>=CinemarrSettings.maximumScreensPerOwner())return "Maximum screens per owner reached";if(preset.physicalPixels()<CinemarrSettings.minimumScreenPixels()||preset.physicalPixels()>CinemarrSettings.maximumScreenPixels()||preset.physicalWidth()>CinemarrSettings.maximumScreenDimension()||preset.physicalHeight()>CinemarrSettings.maximumScreenDimension())return preset.id()+" Quick TV exceeds this server's screen construction limits";Direction front=state.getValue(FACING),across=front.getClockWise();BlockPos anchor=controller.relative(front).relative(across,-(preset.physicalWidth()/2));java.util.Set<Long> footprint=new java.util.HashSet<>();for(int y=0;y<preset.physicalHeight();y++)for(int x=0;x<preset.physicalWidth();x++){BlockPos target=anchor.relative(across,x).above(y);footprint.add(target.asLong());if(!level.hasChunkAt(target))return "Load the complete "+preset.id()+" Quick TV footprint before building";BlockState existing=level.getBlockState(target);if(!existing.isAir())return "The "+preset.id()+" Quick TV footprint is obstructed at "+target.toShortString();}return screens.overlaps(controller,footprint)?"Quick TV pixels already belong to another TV":null;}
    @Override protected void affectNeighborsAfterRemoval(BlockState state,ServerLevel level,BlockPos pos,boolean movedByPiston){BuildJob job=jobs(level).get(pos.asLong());if(job!=null)rollback(level,pos,job);CinemarrWorldScreens.Television removed=CinemarrWorldScreens.get(level).removeController(pos);if(removed!=null)for(Long packed:removed.pixels()){BlockPos pixel=BlockPos.of(packed);if(level.getBlockState(pixel).is(CinemarrBlocks.screenPixel()))level.setBlockAndUpdate(pixel,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());}super.affectNeighborsAfterRemoval(state,level,pos,movedByPiston);}
}
