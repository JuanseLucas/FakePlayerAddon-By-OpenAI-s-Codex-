package dev.fakeplayeraddon.mixin.client;

import de.chunkloader.client.screen.DisabledChunkloadersScreen;
import de.chunkloader.network.payload.DisabledChunkloadersListPayload.DisabledChunkloaderEntry;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = DisabledChunkloadersScreen.class, remap = false)
public interface DisabledScreenAccess {
    @Invoker("getFilteredEntries")
    List<DisabledChunkloaderEntry> fakeplayeraddon$filteredEntries();

    @Accessor("scrollOffset")
    int fakeplayeraddon$scrollOffset();
}
