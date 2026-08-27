package stonytark.cinemarr.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import stonytark.cinemarr.Cinemarr;
import stonytark.cinemarr.screen.ScreenPixelBlock;
import stonytark.cinemarr.screen.TvControllerBlock;

import java.util.function.Function;

public final class CinemarrBlocks {
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, Cinemarr.MODID);
    public static final RegistryObject<Block> SCREEN_PIXEL = register("screen_pixel", properties -> new ScreenPixelBlock(properties.noOcclusion()));
    public static final RegistryObject<Block> TV_CONTROLLER = register("tv_controller", TvControllerBlock::new);
    public static final RegistryObject<Block> TV_CASING = register("tv_casing", Block::new);
    public static final RegistryObject<Block> TV_SPEAKER = register("tv_speaker", Block::new);

    private static <T extends Block> RegistryObject<Block> register(String name, Function<BlockBehaviour.Properties, T> factory) {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK,
                Identifier.fromNamespaceAndPath(Cinemarr.MODID, name));
        return BLOCKS.register(name, () -> factory.apply(BlockBehaviour.Properties.of().setId(key)
                .mapColor(MapColor.COLOR_BLACK).strength(2.0F, 6.0F).sound(SoundType.METAL)));
    }

    public static void register(BusGroup bus) { BLOCKS.register(bus); }
    private CinemarrBlocks() {}
}
