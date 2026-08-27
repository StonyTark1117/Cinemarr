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
    public static final BlockItem REDSTONE_RECEIVER=register("redstone_receiver",CinemarrBlocks.REDSTONE_RECEIVER);
    public static final Item TV_REMOTE=registerItem("tv_remote");
    public static Item tvRemote(){return TV_REMOTE;}
    public static final BlockItem QUICK_TV_144P=register("quick_tv_144p",CinemarrBlocks.QUICK_TV_144P);
    public static final BlockItem QUICK_TV_240P=register("quick_tv_240p",CinemarrBlocks.QUICK_TV_240P);
    public static final BlockItem QUICK_TV_480P=register("quick_tv_480p",CinemarrBlocks.QUICK_TV_480P);
    public static final BlockItem QUICK_TV_720P=register("quick_tv_720p",CinemarrBlocks.QUICK_TV_720P);
    public static final BlockItem QUICK_TV_1080P=register("quick_tv_1080p",CinemarrBlocks.QUICK_TV_1080P);
    public static final BlockItem QUICK_TV_1440P=register("quick_tv_1440p",CinemarrBlocks.QUICK_TV_1440P);
    public static final BlockItem QUICK_TV_4K=register("quick_tv_4k",CinemarrBlocks.QUICK_TV_4K);
    public static final BlockItem QUICK_TV_8K=register("quick_tv_8k",CinemarrBlocks.QUICK_TV_8K);
    private static BlockItem register(String name,net.minecraft.world.level.block.Block block){
        Identifier id=Identifier.fromNamespaceAndPath(Cinemarr.MODID,name);
        ResourceKey<Item> key=ResourceKey.create(Registries.ITEM,id);
        return Registry.register(BuiltInRegistries.ITEM,id,new BlockItem(block,new Item.Properties().useBlockDescriptionPrefix().setId(key)));
    }
    private static Item registerItem(String name){
        Identifier id=Identifier.fromNamespaceAndPath(Cinemarr.MODID,name);
        ResourceKey<Item> key=ResourceKey.create(Registries.ITEM,id);
        return Registry.register(BuiltInRegistries.ITEM,id,new Item(new Item.Properties().stacksTo(1).setId(key)));
    }
    public static void register(){}
    private CinemarrItems(){}
}
