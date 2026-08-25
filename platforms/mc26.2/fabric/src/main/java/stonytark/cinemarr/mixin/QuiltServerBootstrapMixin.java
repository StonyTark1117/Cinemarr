package stonytark.cinemarr.mixin;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import stonytark.cinemarr.Cinemarr;
import stonytark.cinemarr.quilt.QuiltNetworkingCodecRepair;
import stonytark.cinemarr.server.CinemarrCommands;

@Mixin(value = Commands.class, priority = 1100)
abstract class QuiltServerBootstrapMixin {
    @Shadow @Final private CommandDispatcher<CommandSourceStack> dispatcher;

    @Inject(method = "<init>", at = @At(value = "INVOKE", target =
            "Lcom/mojang/brigadier/CommandDispatcher;setConsumer(Lcom/mojang/brigadier/ResultConsumer;)V", shift = At.Shift.AFTER))
    private void cinemarr$bootstrapQuilt(CallbackInfo callback) {
        QuiltNetworkingCodecRepair.install();
        Cinemarr.bootstrapQuilt();
        CinemarrCommands.registerQuiltFallback(dispatcher);
    }
}
