package stonytark.cinemarr.mixin.client;

import com.mojang.blaze3d.audio.Channel;
import net.minecraft.client.sounds.AudioStream;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Channel.class)
public interface ChannelAccessor {
    @Accessor("source") int cinemarr$source();

    @Accessor("stream") void cinemarr$stream(AudioStream stream);

    @Accessor("streamingBufferSize") void cinemarr$streamingBufferSize(int bytes);

    @Invoker("pumpBuffers") void cinemarr$pumpBuffers(int count);
}
