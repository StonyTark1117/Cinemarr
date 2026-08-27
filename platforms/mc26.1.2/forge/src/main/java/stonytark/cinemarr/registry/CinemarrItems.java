package stonytark.cinemarr.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import stonytark.cinemarr.Cinemarr;

public final class CinemarrItems {
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Cinemarr.MODID);
    public static final RegistryObject<Item> SCREEN_PIXEL = register("screen_pixel", CinemarrBlocks.SCREEN_PIXEL);
    public static final RegistryObject<Item> TV_CONTROLLER = register("tv_controller", CinemarrBlocks.TV_CONTROLLER);
    public static final RegistryObject<Item> TV_CASING = register("tv_casing", CinemarrBlocks.TV_CASING);
    public static final RegistryObject<Item> TV_SPEAKER = register("tv_speaker", CinemarrBlocks.TV_SPEAKER);

    private static RegistryObject<Item> register(String name, RegistryObject<Block> block) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM,
                Identifier.fromNamespaceAndPath(Cinemarr.MODID, name));
        return ITEMS.register(name, () -> new BlockItem(block.get(),
                new Item.Properties().useBlockDescriptionPrefix().setId(key)));
    }

    public static void register(BusGroup bus) { ITEMS.register(bus); }
    private CinemarrItems() {}
}
