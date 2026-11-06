package dev.fakeplayeraddon.active;

import de.chunkloader.network.payload.DisabledChunkloadersListPayload.DisabledChunkloaderEntry;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/** Uses the upstream row representation, without sending its disabled-list packet. */
public record ActiveListPayload(List<DisabledChunkloaderEntry> entries)
        implements CustomPacketPayload {
    public ActiveListPayload {
        entries = List.copyOf(entries);
    }

    public static final Type<ActiveListPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("fakeplayeraddon", "active_list"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ActiveListPayload> CODEC =
            StreamCodec.of(
                    (buf, value) -> {
                        buf.writeVarInt(value.entries().size());
                        for (var entry : value.entries()) {
                            entry.write(buf);
                        }
                    },
                    buf -> {
                        int count = buf.readVarInt();
                        if (count < 0 || count > 65536) {
                            throw new IllegalArgumentException(
                                    "Invalid active bot list size: " + count);
                        }
                        var entries = new ArrayList<DisabledChunkloaderEntry>(count);
                        for (int i = 0; i < count; i++) {
                            entries.add(DisabledChunkloaderEntry.read(buf));
                        }
                        return new ActiveListPayload(entries);
                    });

    @Override
    public Type<ActiveListPayload> type() {
        return TYPE;
    }
}
