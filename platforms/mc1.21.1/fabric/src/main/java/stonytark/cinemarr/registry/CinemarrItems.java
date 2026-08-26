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

    public static void register() {
        net.minecraft.core.Registry.register(BuiltInRegistries.ITEM, id("screen_pixel"), SCREEN_PIXEL);
        net.minecraft.core.Registry.register(BuiltInRegistries.ITEM, id("tv_controller"), TV_CONTROLLER);
        net.minecraft.core.Registry.register(BuiltInRegistries.ITEM, id("tv_casing"), TV_CASING);
        net.minecraft.core.Registry.register(BuiltInRegistries.ITEM, id("tv_speaker"), TV_SPEAKER);
    }

    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(Cinemarr.MODID, path); }
    private CinemarrItems() {}
}
