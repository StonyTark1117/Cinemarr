package stonytark.cinemarr.server;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import stonytark.cinemarr.network.CinemarrPayloads;
import java.util.ArrayList;
import java.util.List;

public record StationDefinition(CinemarrPayloads.StationType type, String name,
                                List<CinemarrPayloads.StationSeed> seeds, long generation) {
    public StationDefinition {
        type = type == null ? CinemarrPayloads.StationType.NONE : type;
        name = name == null ? "" : name;
        seeds = seeds == null ? List.of() : List.copyOf(seeds.stream().limit(5).toList());
        generation = Math.max(0, generation);
    }

    public static StationDefinition none(long generation) {
        return new StationDefinition(CinemarrPayloads.StationType.NONE, "", List.of(), generation);
    }

    public boolean active() { return type != CinemarrPayloads.StationType.NONE; }
    public boolean adventure() { return type == CinemarrPayloads.StationType.SONIC_ADVENTURE; }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", type.name()); tag.putString("name", name); tag.putLong("generation", generation);
        ListTag seedTags = new ListTag();
        for (CinemarrPayloads.StationSeed seed : seeds) {
            CompoundTag value = new CompoundTag();
            value.putString("kind", seed.kind().name()); value.putString("key", seed.key());
            value.putString("title", seed.title()); value.putString("subtitle", seed.subtitle()); seedTags.add(value);
        }
        tag.put("seeds", seedTags);
        return tag;
    }

    public static StationDefinition load(CompoundTag tag) {
        CinemarrPayloads.StationType type;
        try { type = CinemarrPayloads.StationType.valueOf(tag.getStringOr("type", "NONE")); }
        catch (IllegalArgumentException invalid) { type = CinemarrPayloads.StationType.NONE; }
        List<CinemarrPayloads.StationSeed> seeds = new ArrayList<>();
        ListTag values = tag.getListOrEmpty("seeds");
        for (int i = 0; i < Math.min(5, values.size()); i++) {
            CompoundTag value = values.getCompoundOrEmpty(i);
            try {
                seeds.add(new CinemarrPayloads.StationSeed(CinemarrPayloads.ItemKind.valueOf(value.getStringOr("kind", "")),
                        value.getStringOr("key", ""), value.getStringOr("title", ""), value.getStringOr("subtitle", "")));
            } catch (IllegalArgumentException ignored) {}
        }
        return new StationDefinition(type, tag.getStringOr("name", ""), seeds, tag.getLongOr("generation", 0L));
    }
}
