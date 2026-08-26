package stonytark.cinemarr.registry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import stonytark.cinemarr.Cinemarr;
import stonytark.cinemarr.screen.ScreenPixelBlock;
import stonytark.cinemarr.screen.TvControllerBlock;

public final class CinemarrBlocks {
    private static BlockBehaviour.Properties electronic(){return BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(2.0F,6.0F).sound(SoundType.METAL);}
    public static final ScreenPixelBlock SCREEN_PIXEL=new ScreenPixelBlock(electronic().noOcclusion());
    public static final TvControllerBlock TV_CONTROLLER=new TvControllerBlock(electronic());
    public static final Block TV_CASING=new Block(electronic());
    public static final Block TV_SPEAKER=new Block(electronic());
    public static void register(){Registry.register(BuiltInRegistries.BLOCK,id("screen_pixel"),SCREEN_PIXEL);Registry.register(BuiltInRegistries.BLOCK,id("tv_controller"),TV_CONTROLLER);Registry.register(BuiltInRegistries.BLOCK,id("tv_casing"),TV_CASING);Registry.register(BuiltInRegistries.BLOCK,id("tv_speaker"),TV_SPEAKER);}
    private static ResourceLocation id(String path){return new ResourceLocation(Cinemarr.MODID,path);}
    private CinemarrBlocks(){}
}
