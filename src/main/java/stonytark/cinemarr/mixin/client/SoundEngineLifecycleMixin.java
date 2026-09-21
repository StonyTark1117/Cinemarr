package stonytark.cinemarr.mixin.client;

import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import stonytark.cinemarr.core.client.AudioEngineGeneration;

@Mixin(SoundEngine.class)
public abstract class SoundEngineLifecycleMixin {
    @Inject(method = "destroy", at = @At("HEAD"))
    private void cinemarr$invalidateNativeChannels(CallbackInfo callback) {
        AudioEngineGeneration.destroying();
    }
}
