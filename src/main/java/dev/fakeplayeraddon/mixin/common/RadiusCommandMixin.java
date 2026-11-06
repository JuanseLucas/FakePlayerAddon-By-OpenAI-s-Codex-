package dev.fakeplayeraddon.mixin.common;

import de.chunkloader.commands.ChunkloaderCommand;
import dev.fakeplayeraddon.loading.BotRadius;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(value = ChunkloaderCommand.class, remap = false)
public abstract class RadiusCommandMixin {
    @ModifyConstant(method = "registerCommands", constant = @Constant(intValue = 3), require = 2)
    private static int fakeplayeraddon$commandRange(int original) { return BotRadius.MAX; }

    @ModifyConstant(method = "setRadius", constant = @Constant(intValue = 3, ordinal = 0))
    private static int fakeplayeraddon$commandValidation(int original) { return BotRadius.MAX; }

    @ModifyArg(method = "setRadius", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/chat/Component;literal(Ljava/lang/String;)Lnet/minecraft/network/chat/MutableComponent;"), index = 0)
    private static String fakeplayeraddon$commandError(String message) {
        return message.startsWith("Invalid radius:") ? message.replace("between 0 and 3", "between 0 and " + BotRadius.MAX) : message;
    }
}
