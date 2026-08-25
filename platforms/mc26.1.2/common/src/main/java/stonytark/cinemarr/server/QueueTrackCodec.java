package stonytark.cinemarr.server;

import net.minecraft.nbt.CompoundTag;
import stonytark.cinemarr.core.model.QueueTrack;
import stonytark.cinemarr.network.CinemarrPayloads;

final class QueueTrackCodec {
    static CinemarrPayloads.QueueEntry networkEntry(QueueTrack track) {
        return networkEntry(track, CinemarrPayloads.PlaybackOrigin.MANUAL, true);
    }

    static CinemarrPayloads.QueueEntry networkEntry(QueueTrack track, CinemarrPayloads.PlaybackOrigin source, boolean editable) {
        return new CinemarrPayloads.QueueEntry(track.key(), track.title(), track.artist(), track.durationMs(), source, editable);
    }

    static CompoundTag save(QueueTrack track) {
        CompoundTag tag = new CompoundTag();
        tag.putString("key", track.key()); tag.putString("title", track.title()); tag.putString("artist", track.artist());
        tag.putString("album", track.album()); tag.putLong("duration", track.durationMs());
        return tag;
    }

    static QueueTrack load(CompoundTag tag) {
        return new QueueTrack(tag.getStringOr("key", ""), tag.getStringOr("title", ""), tag.getStringOr("artist", ""), tag.getStringOr("album", ""), tag.getLongOr("duration", 0L));
    }

    private QueueTrackCodec() {}
}
