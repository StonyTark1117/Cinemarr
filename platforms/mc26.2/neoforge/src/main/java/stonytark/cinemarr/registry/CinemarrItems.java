package stonytark.cinemarr.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import stonytark.cinemarr.Cinemarr;

public final class CinemarrItems {
    public static final DeferredRegister.Items REGISTER = DeferredRegister.createItems(Cinemarr.MODID);
    public static final DeferredItem<BlockItem> SCREEN_PIXEL = register("screen_pixel", CinemarrBlocks.SCREEN_PIXEL);
    public static final DeferredItem<BlockItem> TV_CONTROLLER = register("tv_controller", CinemarrBlocks.TV_CONTROLLER);
    public static final DeferredItem<BlockItem> TV_CASING = register("tv_casing", CinemarrBlocks.TV_CASING);
    public static final DeferredItem<BlockItem> TV_SPEAKER = register("tv_speaker", CinemarrBlocks.TV_SPEAKER);

    private static DeferredItem<BlockItem> register(String name,
            net.neoforged.neoforge.registries.DeferredHolder<net.minecraft.world.level.block.Block,
                    ? extends net.minecraft.world.level.block.Block> block) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM,
                Identifier.fromNamespaceAndPath(Cinemarr.MODID, name));
        return REGISTER.register(name, () -> new BlockItem(block.get(),
                new Item.Properties().useBlockDescriptionPrefix().setId(key)));
    }
    private CinemarrItems() {}
}
