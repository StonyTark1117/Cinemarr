package stonytark.cinemarr.registry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import stonytark.cinemarr.Cinemarr;

public final class CinemarrItems {
    public static final BlockItem SCREEN_PIXEL=register("screen_pixel",CinemarrBlocks.SCREEN_PIXEL);
    public static final BlockItem TV_CONTROLLER=register("tv_controller",CinemarrBlocks.TV_CONTROLLER);
    public static final BlockItem TV_CASING=register("tv_casing",CinemarrBlocks.TV_CASING);
    public static final BlockItem TV_SPEAKER=register("tv_speaker",CinemarrBlocks.TV_SPEAKER);
    private static BlockItem register(String name,net.minecraft.world.level.block.Block block){
        Identifier id=Identifier.fromNamespaceAndPath(Cinemarr.MODID,name);
        ResourceKey<Item> key=ResourceKey.create(Registries.ITEM,id);
        return Registry.register(BuiltInRegistries.ITEM,id,new BlockItem(block,new Item.Properties().useBlockDescriptionPrefix().setId(key)));
    }
    public static void register(){}
    private CinemarrItems(){}
}
