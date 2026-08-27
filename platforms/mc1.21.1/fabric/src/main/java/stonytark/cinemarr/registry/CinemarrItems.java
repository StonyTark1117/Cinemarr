package stonytark.cinemarr.registry;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import stonytark.cinemarr.Cinemarr;

public final class CinemarrItems {
    public static final BlockItem SCREEN_PIXEL = new BlockItem(CinemarrBlocks.SCREEN_PIXEL, new Item.Properties());
    public static final BlockItem TV_CONTROLLER = new BlockItem(CinemarrBlocks.TV_CONTROLLER, new Item.Properties());
    public static final BlockItem TV_CASING = new BlockItem(CinemarrBlocks.TV_CASING, new Item.Properties());
    public static final BlockItem TV_SPEAKER = new BlockItem(CinemarrBlocks.TV_SPEAKER, new Item.Properties());
    public static final BlockItem REDSTONE_RECEIVER = new BlockItem(CinemarrBlocks.REDSTONE_RECEIVER, new Item.Properties());
    public static final Item TV_REMOTE = new Item(new Item.Properties().stacksTo(1));
    public static Item tvRemote() { return TV_REMOTE; }
    public static final BlockItem QUICK_TV_144P = quick(CinemarrBlocks.QUICK_TV_144P);
    public static final BlockItem QUICK_TV_240P = quick(CinemarrBlocks.QUICK_TV_240P);
    public static final BlockItem QUICK_TV_480P = quick(CinemarrBlocks.QUICK_TV_480P);
    public static final BlockItem QUICK_TV_720P = quick(CinemarrBlocks.QUICK_TV_720P);
    public static final BlockItem QUICK_TV_1080P = quick(CinemarrBlocks.QUICK_TV_1080P);
    public static final BlockItem QUICK_TV_1440P = quick(CinemarrBlocks.QUICK_TV_1440P);
    public static final BlockItem QUICK_TV_4K = quick(CinemarrBlocks.QUICK_TV_4K);
    public static final BlockItem QUICK_TV_8K = quick(CinemarrBlocks.QUICK_TV_8K);

    public static void register() {
        net.minecraft.core.Registry.register(BuiltInRegistries.ITEM, id("screen_pixel"), SCREEN_PIXEL);
        net.minecraft.core.Registry.register(BuiltInRegistries.ITEM, id("tv_controller"), TV_CONTROLLER);
        net.minecraft.core.Registry.register(BuiltInRegistries.ITEM, id("tv_casing"), TV_CASING);
        net.minecraft.core.Registry.register(BuiltInRegistries.ITEM, id("tv_speaker"), TV_SPEAKER);
        net.minecraft.core.Registry.register(BuiltInRegistries.ITEM, id("redstone_receiver"), REDSTONE_RECEIVER);
        net.minecraft.core.Registry.register(BuiltInRegistries.ITEM, id("tv_remote"), TV_REMOTE);
        registerQuick("144p", QUICK_TV_144P); registerQuick("240p", QUICK_TV_240P);
        registerQuick("480p", QUICK_TV_480P); registerQuick("720p", QUICK_TV_720P);
        registerQuick("1080p", QUICK_TV_1080P); registerQuick("1440p", QUICK_TV_1440P);
        registerQuick("4k", QUICK_TV_4K); registerQuick("8k", QUICK_TV_8K);
    }

    private static BlockItem quick(net.minecraft.world.level.block.Block block) { return new BlockItem(block, new Item.Properties()); }
    private static void registerQuick(String id, BlockItem item) {
        net.minecraft.core.Registry.register(BuiltInRegistries.ITEM, id("quick_tv_" + id), item);
    }

    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(Cinemarr.MODID, path); }
    private CinemarrItems() {}
}
