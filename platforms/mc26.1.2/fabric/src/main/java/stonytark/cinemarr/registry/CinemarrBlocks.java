package stonytark.cinemarr.registry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import stonytark.cinemarr.Cinemarr;
import stonytark.cinemarr.screen.ScreenPixelBlock;
import stonytark.cinemarr.screen.TvControllerBlock;

import java.util.function.Function;

public final class CinemarrBlocks {
    public static final ScreenPixelBlock SCREEN_PIXEL=register("screen_pixel",p->new ScreenPixelBlock(p.noOcclusion()));
    public static final TvControllerBlock TV_CONTROLLER=register("tv_controller",TvControllerBlock::new);
    public static final Block TV_CASING=register("tv_casing",Block::new);
    public static final Block TV_SPEAKER=register("tv_speaker",Block::new);

    private static <T extends Block>T register(String name,Function<BlockBehaviour.Properties,T> factory){
        Identifier id=Identifier.fromNamespaceAndPath(Cinemarr.MODID,name);
        ResourceKey<Block> key=ResourceKey.create(Registries.BLOCK,id);
        T block=factory.apply(BlockBehaviour.Properties.of().setId(key).mapColor(MapColor.COLOR_BLACK).strength(2.0F,6.0F).sound(SoundType.METAL));
        return Registry.register(BuiltInRegistries.BLOCK,id,block);
    }
    public static void register(){}
    private CinemarrBlocks(){}
}
