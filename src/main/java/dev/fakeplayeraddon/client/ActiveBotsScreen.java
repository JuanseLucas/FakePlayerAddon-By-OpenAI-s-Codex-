package dev.fakeplayeraddon.client;

import de.chunkloader.client.screen.ChunkloaderConfirmationScreen;
import de.chunkloader.client.screen.DisabledChunkloadersScreen;
import de.chunkloader.network.payload.DisabledChunkloadersListPayload.DisabledChunkloaderEntry;
import dev.fakeplayeraddon.active.ActiveRequestPayload;
import dev.fakeplayeraddon.mixin.client.DisabledScreenAccess;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Keeps the original screen's renderer, search, scrolling, colors, and Close button. */
public final class ActiveBotsScreen extends DisabledChunkloadersScreen {
    private List<DisabledChunkloaderEntry> currentRows;

    public ActiveBotsScreen(List<DisabledChunkloaderEntry> rows) {
        super(rows);
        currentRows = List.copyOf(rows);
    }

    @Override
    public Component getTitle() {
        return Component.literal("Active Fakeplayer/Chunkplayer");
    }

    /** An in-flight F8 response must never populate the active screen. */
    @Override
    public void updateDisabledChunkloaders(List<DisabledChunkloaderEntry> ignored) {
    }

    public void updateActiveEntries(List<DisabledChunkloaderEntry> rows) {
        if (!currentRows.equals(rows)) {
            currentRows = List.copyOf(rows);
            super.updateDisabledChunkloaders(rows);
        }
    }

    @Override
    protected void init() {
        super.init();
        var access = (DisabledScreenAccess) (Object) this;
        var rows = access.fakeplayeraddon$filteredEntries();
        int index = access.fakeplayeraddon$scrollOffset();
        Map<Integer, DisabledChunkloaderEntry> rowAtButtonY = new HashMap<>();
        var originalWidgets = new ArrayList<>(children());
        for (var widget : originalWidgets) {
            if (widget instanceof Button button && button.getMessage().getString().equals("Restore")
                    && index < rows.size()) {
                rowAtButtonY.put(button.getY(), rows.get(index++));
            }
        }
        for (var widget : originalWidgets) {
            if (!(widget instanceof Button button)) {
                continue;
            }
            String label = button.getMessage().getString();
            if (label.equals("Edit")) {
                removeWidget(button);
                continue;
            }
            var entry = rowAtButtonY.get(button.getY());
            if (entry == null) {
                continue;
            }
            Button replacement;
            if (label.equals("Restore")) {
                replacement = Button.builder(Component.literal("Configure"), ignored ->
                        ActiveBotsClient.send(ActiveRequestPayload.Operation.CONFIGURE, entry)).bounds(
                        button.getX(), button.getY(), button.getWidth(), button.getHeight()).build();
            } else if (label.equals("Delete")) {
                replacement = Button.builder(button.getMessage(), ignored -> confirmDelete(entry)).bounds(
                        button.getX(), button.getY(), button.getWidth(), button.getHeight()).build();
            } else {
                continue;
            }
            removeWidget(button);
            addRenderableWidget(replacement);
        }
    }

    private void confirmDelete(DisabledChunkloaderEntry entry) {
        String name = entry.name() == null ? "Unnamed" : entry.name();
        minecraft.gui.setScreen(new ChunkloaderConfirmationScreen(this,
                Component.literal("Delete Player?").withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
                Component.literal("Are you sure you want to permanently delete\n" + name + "?"),
                () -> {
                    // Leave removal to the server, so a rejected/stale request cannot
                    // make an active bot disappear from the client list.
                    ActiveBotsClient.send(ActiveRequestPayload.Operation.DELETE, entry);
                    minecraft.gui.setScreen(this);
                }, null));
    }
}
