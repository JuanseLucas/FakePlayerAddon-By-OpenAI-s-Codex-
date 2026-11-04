package dev.fakeplayeraddon.mixin.common;

import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.world.level.TicketStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerChunkCache.class)
public interface ChunkTicketAccess {
    @Accessor("ticketStorage") TicketStorage fakeplayeraddon$tickets();
}
